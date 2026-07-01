#!/usr/bin/env python3
"""Synthetic multi-patient PGHD scalability/correctness simulation.

This is not a production benchmark. It models concurrent PGHD batch submissions
from multiple synthetic patients and verifies that backend-like state remains
partitioned by patient address, batch IDs are idempotent, and no batch is stored
under the wrong patient.
"""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import dataclasses
import hashlib
import hmac
import json
import random
import secrets
import threading
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


@dataclasses.dataclass(frozen=True)
class SyntheticPatient:
    index: int
    nik: str
    address: str
    signing_secret: bytes


@dataclasses.dataclass(frozen=True)
class SyntheticSubmit:
    patient_address: str
    batch_id: str
    enc_pghd: str
    h_cipher: str
    signature: str
    record_count: int


class SimulatedPghdBackend:
    def __init__(self, patients: list[SyntheticPatient], transient_failure_rate: float) -> None:
        self._signing_secrets = {p.address: p.signing_secret for p in patients}
        self._transient_failure_rate = transient_failure_rate
        self._lock = threading.Lock()
        self._metadata_by_patient: dict[str, list[dict[str, Any]]] = defaultdict(list)
        self._batch_to_patient: dict[str, str] = {}
        self._cid_store: dict[str, str] = {}
        self.accepted = 0
        self.duplicates = 0
        self.transient_failures = 0

    def submit(self, payload: SyntheticSubmit) -> str:
        if random.random() < self._transient_failure_rate:
            with self._lock:
                self.transient_failures += 1
            raise RuntimeError("simulated transient backend failure")

        enc_bytes = base64.b64decode(payload.enc_pghd.encode("ascii"), validate=True)
        computed_h_cipher = hashlib.sha256(enc_bytes).hexdigest()
        if computed_h_cipher != payload.h_cipher:
            raise ValueError(f"h_cipher mismatch for {payload.batch_id}")

        secret = self._signing_secrets.get(payload.patient_address)
        if secret is None:
            raise ValueError(f"unknown patient {payload.patient_address}")

        expected_signature = hmac.new(
            secret,
            payload.h_cipher.encode("ascii"),
            hashlib.sha256,
        ).hexdigest()
        if not hmac.compare_digest(expected_signature, payload.signature):
            raise ValueError(f"signature mismatch for {payload.batch_id}")

        cid = "bafy-sim-" + hashlib.sha256(
            f"{payload.patient_address}:{payload.batch_id}:{payload.h_cipher}".encode("utf-8")
        ).hexdigest()[:48]

        with self._lock:
            existing_patient = self._batch_to_patient.get(payload.batch_id)
            if existing_patient is not None:
                if existing_patient != payload.patient_address:
                    raise AssertionError(
                        f"batch {payload.batch_id} already belongs to {existing_patient}, "
                        f"but was submitted by {payload.patient_address}"
                    )
                self.duplicates += 1
                return cid

            self._batch_to_patient[payload.batch_id] = payload.patient_address
            self._cid_store[cid] = payload.enc_pghd
            self._metadata_by_patient[payload.patient_address].append(
                {
                    "batch_id": payload.batch_id,
                    "cid": cid,
                    "h_cipher": payload.h_cipher,
                    "patient_iota_address": payload.patient_address,
                    "record_count": payload.record_count,
                }
            )
            self.accepted += 1
            return cid

    def list_patient_batches(self, patient_address: str) -> list[dict[str, Any]]:
        with self._lock:
            return list(self._metadata_by_patient.get(patient_address, []))

    def verify_partitioning(
        self,
        patients: list[SyntheticPatient],
        expected_batches_per_patient: int,
    ) -> None:
        total = 0
        all_batch_ids: list[str] = []
        for patient in patients:
            rows = self.list_patient_batches(patient.address)
            if len(rows) != expected_batches_per_patient:
                raise AssertionError(
                    f"{patient.address} has {len(rows)} batches, "
                    f"expected {expected_batches_per_patient}"
                )
            for row in rows:
                if row["patient_iota_address"] != patient.address:
                    raise AssertionError(
                        f"metadata leak: {row['batch_id']} stored under wrong patient"
                    )
                all_batch_ids.append(row["batch_id"])
            total += len(rows)

        counts = Counter(all_batch_ids)
        duplicates = [batch_id for batch_id, count in counts.items() if count > 1]
        if duplicates:
            raise AssertionError(f"duplicate stored batch IDs: {duplicates[:5]}")

        expected_total = len(patients) * expected_batches_per_patient
        if total != expected_total:
            raise AssertionError(f"stored {total} batches, expected {expected_total}")


def make_patients(count: int) -> list[SyntheticPatient]:
    patients: list[SyntheticPatient] = []
    for index in range(count):
        nik = f"{9_000_000_000_000_000 + index:016d}"
        address = "0x" + hashlib.sha256(f"patient-{nik}".encode("utf-8")).hexdigest()
        patients.append(
            SyntheticPatient(
                index=index,
                nik=nik,
                address=address,
                signing_secret=secrets.token_bytes(32),
            )
        )
    return patients


def make_submit(
    patient: SyntheticPatient,
    batch_index: int,
    records_per_batch: int,
) -> SyntheticSubmit:
    batch_id = f"sim-p{patient.index:03d}-b{batch_index:04d}"
    now = int(time.time() * 1000)
    pghd_payload = {
        "schema_version": "sim-1",
        "batch_id": batch_id,
        "patient_iota_address": patient.address,
        "patient_test_nik": patient.nik,
        "batch_period": {
            "start_timestamp": now - records_per_batch * 60_000,
            "end_timestamp": now,
        },
        "data_group": [
            {
                "measurement_type": "steps",
                "unit": "count",
                "source": "synthetic_scalability",
                "device_type": "simulator",
                "recording_method": "synthetic",
                "data_points": [
                    {
                        "timestamp": now - (records_per_batch - i) * 60_000,
                        "value": 100 + i + patient.index,
                        "unit": "count",
                    }
                    for i in range(records_per_batch)
                ],
            }
        ],
    }
    enc_bytes = json.dumps(
        pghd_payload,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    enc_pghd = base64.b64encode(enc_bytes).decode("ascii")
    h_cipher = hashlib.sha256(enc_bytes).hexdigest()
    signature = hmac.new(
        patient.signing_secret,
        h_cipher.encode("ascii"),
        hashlib.sha256,
    ).hexdigest()
    return SyntheticSubmit(
        patient_address=patient.address,
        batch_id=batch_id,
        enc_pghd=enc_pghd,
        h_cipher=h_cipher,
        signature=signature,
        record_count=records_per_batch,
    )


def run_simulation(args: argparse.Namespace) -> None:
    random.seed(args.seed)
    patients = make_patients(args.patients)
    backend = SimulatedPghdBackend(
        patients=patients,
        transient_failure_rate=args.transient_failure_rate,
    )
    submissions = [
        make_submit(patient, batch_index, args.records_per_batch)
        for patient in patients
        for batch_index in range(args.batches_per_patient)
    ]
    if args.include_duplicate_retries:
        submissions.extend(submissions[: max(1, min(len(submissions), args.patients))])
    random.shuffle(submissions)

    attempts = 0
    attempts_lock = threading.Lock()
    permanent_errors: list[str] = []

    def submit_with_retry(payload: SyntheticSubmit) -> None:
        nonlocal attempts
        for attempt in range(args.max_retries + 1):
            with attempts_lock:
                attempts += 1
            try:
                backend.submit(payload)
                return
            except RuntimeError:
                if attempt >= args.max_retries:
                    permanent_errors.append(
                        f"{payload.batch_id} exceeded retries after transient failures"
                    )
                    return
                time.sleep(args.retry_sleep_ms / 1000.0)
            except Exception as exc:  # noqa: BLE001 - this script reports all validation failures
                permanent_errors.append(f"{payload.batch_id}: {exc}")
                return

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        list(executor.map(submit_with_retry, submissions))
    elapsed = time.perf_counter() - started

    if permanent_errors:
        for error in permanent_errors[:10]:
            print(f"[FAIL] {error}")
        raise SystemExit(1)

    backend.verify_partitioning(
        patients=patients,
        expected_batches_per_patient=args.batches_per_patient,
    )

    total_unique = args.patients * args.batches_per_patient
    total_records = total_unique * args.records_per_batch
    print("[PASS] Synthetic multi-patient PGHD scalability simulation finished")
    print(f"[INFO] patients={args.patients}")
    print(f"[INFO] patient_nik_range={patients[0].nik}..{patients[-1].nik}")
    print(f"[INFO] unique_batches={total_unique}")
    print(f"[INFO] records={total_records}")
    print(f"[INFO] concurrency={args.concurrency}")
    print(f"[INFO] accepted_unique_batches={backend.accepted}")
    print(f"[INFO] duplicate_retries={backend.duplicates}")
    print(f"[INFO] transient_failures={backend.transient_failures}")
    print(f"[INFO] total_submit_attempts={attempts}")
    print(f"[INFO] elapsed_seconds={elapsed:.3f}")
    print(f"[INFO] simulated_throughput_batches_per_second={total_unique / elapsed:.2f}")


def post_json(url: str, payload: dict[str, Any], timeout: float) -> tuple[int, str]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")


def run_live_payload_replay(args: argparse.Namespace) -> None:
    payload_dir = args.live_payload_dir
    paths = sorted(payload_dir.glob("*.json"))
    if not paths:
        raise SystemExit(f"[FAIL] no JSON payloads found in {payload_dir}")

    url = args.pre_base_url.rstrip("/") + "/api/v1/pghd/submit"
    payloads: list[dict[str, Any]] = []
    for path in paths:
        with path.open("r", encoding="utf-8") as fh:
            payloads.append(json.load(fh))

    failures: list[str] = []
    started = time.perf_counter()

    def send(payload: dict[str, Any]) -> None:
        status, body = post_json(url, payload, args.live_timeout_seconds)
        if status < 200 or status >= 300:
            failures.append(
                f"{payload.get('batch_id', '<unknown>')} returned HTTP {status}: {body[:300]}"
            )

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        list(executor.map(send, payloads))

    elapsed = time.perf_counter() - started
    if failures:
        for failure in failures[:10]:
            print(f"[FAIL] {failure}")
        raise SystemExit(1)

    patients = {payload.get("patient_iota_address") for payload in payloads}
    print("[PASS] Live PGHD payload replay finished")
    print(f"[INFO] endpoint={url}")
    print(f"[INFO] payloads={len(payloads)}")
    print(f"[INFO] patients={len(patients)}")
    print(f"[INFO] concurrency={args.concurrency}")
    print(f"[INFO] elapsed_seconds={elapsed:.3f}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run synthetic or live PGHD scalability simulation.",
    )
    parser.add_argument("--patients", type=int, default=20)
    parser.add_argument("--batches-per-patient", type=int, default=1)
    parser.add_argument("--records-per-batch", type=int, default=60)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--max-retries", type=int, default=3)
    parser.add_argument("--retry-sleep-ms", type=int, default=25)
    parser.add_argument("--transient-failure-rate", type=float, default=0.05)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--include-duplicate-retries", action="store_true", default=False)
    parser.add_argument("--no-duplicate-retries", dest="include_duplicate_retries", action="store_false")
    parser.add_argument("--live-payload-dir", type=Path)
    parser.add_argument("--pre-base-url", default="http://127.0.0.1:4100")
    parser.add_argument("--live-timeout-seconds", type=float, default=30.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.live_payload_dir:
        run_live_payload_replay(args)
    else:
        run_simulation(args)


if __name__ == "__main__":
    main()
