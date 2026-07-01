#!/usr/bin/env python3
"""Run a live concurrent PGHD submit stress test against PRE."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def post_json(url: str, payload: dict[str, Any], timeout: float) -> tuple[int, str, float]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            return response.status, body, time.perf_counter() - started
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, body, time.perf_counter() - started
    except Exception as exc:  # noqa: BLE001 - stress tests report transport errors too
        return 0, repr(exc), time.perf_counter() - started


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((pct / 100.0) * (len(ordered) - 1))))
    return ordered[index]


def load_fixture(path: Path) -> dict[str, Any]:
    fixture = json.loads(path.read_text(encoding="utf-8"))
    if fixture.get("schema_version") != "pre-stress-fixture-v1":
        raise SystemExit(f"[FAIL] unsupported fixture schema in {path}")
    return fixture


def flatten_payloads(fixture: dict[str, Any], limit: int | None) -> list[dict[str, Any]]:
    payloads: list[dict[str, Any]] = []
    for patient in fixture["patients"]:
        payloads.extend(patient.get("payloads", []))
    if limit is not None:
        payloads = payloads[:limit]
    if not payloads:
        raise SystemExit("[FAIL] no payloads found in fixture")
    return payloads


def register_patient_keys(
    fixture: dict[str, Any],
    pre_base_url: str,
    timeout: float,
    concurrency: int,
    progress_every: int,
) -> list[dict[str, Any]]:
    url = pre_base_url.rstrip("/") + "/api/v1/pghd/patient"
    requests = [
        {
            "patient_id_hash": patient["patient_id_hash"],
            "patient_iota_address": patient["patient_iota_address"],
            "pghd_public_key": patient["pghd_public_key_der_base64"],
            "pghd_pre_public_key": patient["pghd_public_key_der_base64"],
        }
        for patient in fixture["patients"]
    ]

    def send(payload: dict[str, Any]) -> dict[str, Any]:
        status, body, latency = post_json(url, payload, timeout)
        return {
            "patient_iota_address": payload["patient_iota_address"],
            "status": status,
            "latency_ms": round(latency * 1000, 3),
            "body": body[:1000],
        }

    results: list[dict[str, Any]] = []
    total = len(requests)
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(send, request) for request in requests]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
            done = len(results)
            if done == 1 or done % progress_every == 0 or done == total:
                success = sum(1 for row in results if 200 <= int(row["status"]) <= 299)
                failed = done - success
                print(
                    f"[INFO] register progress {done}/{total} "
                    f"success={success} failed={failed}",
                    flush=True,
                )
    return results


def submit_payloads(
    payloads: list[dict[str, Any]],
    pre_base_url: str,
    timeout: float,
    concurrency: int,
    progress_every: int,
) -> list[dict[str, Any]]:
    url = pre_base_url.rstrip("/") + "/api/v1/pghd/submit"

    def send(payload: dict[str, Any]) -> dict[str, Any]:
        status, body, latency = post_json(url, payload, timeout)
        result: dict[str, Any] = {
            "batch_id": payload.get("batch_id"),
            "patient_iota_address": payload.get("patient_iota_address"),
            "status": status,
            "latency_ms": round(latency * 1000, 3),
            "body": body[:2000],
        }
        try:
            parsed = json.loads(body)
            result["response_json"] = parsed
        except json.JSONDecodeError:
            pass
        return result

    results: list[dict[str, Any]] = []
    total = len(payloads)
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(send, payload) for payload in payloads]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
            done = len(results)
            if done == 1 or done % progress_every == 0 or done == total:
                status_counts = Counter(str(result["status"]) for result in results)
                success = sum(1 for result in results if 200 <= int(result["status"]) <= 299)
                failed = done - success
                elapsed = max(time.perf_counter() - started, 0.001)
                throughput = done / elapsed
                print(
                    f"[INFO] submit progress {done}/{total} "
                    f"success={success} failed={failed} "
                    f"status_counts={dict(sorted(status_counts.items()))} "
                    f"rps={throughput:.2f}",
                    flush=True,
                )
    return results


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    latencies = [float(result["latency_ms"]) for result in results]
    status_counts = Counter(str(result["status"]) for result in results)
    failure_kind_counts = Counter(
        classify_failure(result.get("body", ""))
        for result in results
        if not 200 <= int(result["status"]) <= 299
    )
    success = sum(1 for result in results if 200 <= int(result["status"]) <= 299)
    return {
        "total": len(results),
        "success": success,
        "failed": len(results) - success,
        "status_counts": dict(sorted(status_counts.items())),
        "failure_kind_counts": dict(sorted(failure_kind_counts.items())),
        "latency_ms": {
            "min": round(min(latencies), 3) if latencies else 0,
            "avg": round(statistics.fmean(latencies), 3) if latencies else 0,
            "p50": round(percentile(latencies, 50), 3),
            "p95": round(percentile(latencies, 95), 3),
            "p99": round(percentile(latencies, 99), 3),
            "max": round(max(latencies), 3) if latencies else 0,
        },
    }


def classify_failure(body: str) -> str:
    lowered = body.lower()
    if "is not available for consumption" in lowered and "current version" in lowered:
        return "iota_object_version_conflict"
    if "balance of gas object" in lowered or "gas" in lowered and "lower than the needed amount" in lowered:
        return "gas_budget_or_balance"
    if "proxycap not found" in lowered:
        return "missing_proxy_cap"
    if "signature" in lowered and "invalid" in lowered:
        return "invalid_signature"
    if "timed out" in lowered or "timeout" in lowered:
        return "timeout"
    return "other"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Submit PGHD fixtures concurrently to PRE.")
    parser.add_argument("--fixture", type=Path, default=Path("tmp/pghd-pre-stress-fixtures.json"))
    parser.add_argument("--pre-base-url", default="http://127.0.0.1:4100")
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--timeout-seconds", type=float, default=120.0)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--register-pghd-patient", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("tmp/pghd-pre-stress-results.json"))
    parser.add_argument("--fail-on-error", action="store_true")
    parser.add_argument("--progress-every", type=int, default=10)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    fixture = load_fixture(args.fixture)
    payloads = flatten_payloads(fixture, args.limit)

    register_results: list[dict[str, Any]] = []
    if args.register_pghd_patient:
        register_results = register_patient_keys(
            fixture,
            args.pre_base_url,
            args.timeout_seconds,
            args.concurrency,
            max(args.progress_every, 1),
        )
        failed_registers = [row for row in register_results if not 200 <= int(row["status"]) <= 299]
        if failed_registers:
            print(f"[FAIL] {len(failed_registers)} PGHD public key registrations failed")
            for row in failed_registers[:5]:
                print(f"[FAIL] register {row['patient_iota_address']} HTTP {row['status']}: {row['body']}")
            raise SystemExit(1)
        print(f"[PASS] registered PGHD public keys in PRE cache: {len(register_results)}")

    started = time.perf_counter()
    submit_results = submit_payloads(
        payloads,
        args.pre_base_url,
        args.timeout_seconds,
        args.concurrency,
        max(args.progress_every, 1),
    )
    elapsed = time.perf_counter() - started
    summary = summarize(submit_results)
    summary["elapsed_seconds"] = round(elapsed, 3)
    summary["throughput_requests_per_second"] = round(len(submit_results) / elapsed, 3) if elapsed else 0
    summary["concurrency"] = args.concurrency
    summary["pre_base_url"] = args.pre_base_url
    summary["fixture"] = str(args.fixture)
    summary["generated_at"] = utc_now_iso()

    report = {
        "summary": summary,
        "register_results": register_results,
        "submit_results": submit_results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print("[INFO] PRE PGHD stress test finished")
    print(f"[INFO] endpoint={args.pre_base_url.rstrip('/')}/api/v1/pghd/submit")
    print(f"[INFO] total={summary['total']}")
    print(f"[INFO] success={summary['success']}")
    print(f"[INFO] failed={summary['failed']}")
    print(f"[INFO] status_counts={summary['status_counts']}")
    print(f"[INFO] failure_kind_counts={summary['failure_kind_counts']}")
    print(f"[INFO] latency_ms={summary['latency_ms']}")
    print(f"[INFO] throughput_requests_per_second={summary['throughput_requests_per_second']}")
    print(f"[INFO] output={args.output}")

    if summary["failed"]:
        print("[INFO] first failures:")
        for row in [result for result in submit_results if not 200 <= int(result["status"]) <= 299][:5]:
            print(f"[INFO] {row['batch_id']} HTTP {row['status']}: {row['body'][:300]}")
        if args.fail_on_error:
            raise SystemExit(1)
    else:
        print("[PASS] all PRE PGHD stress submits succeeded")


if __name__ == "__main__":
    main()
