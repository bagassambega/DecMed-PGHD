#!/usr/bin/env python3
import argparse
import base64
import json
import os
from pathlib import Path
from urllib import error, request


def flip_text(value: str) -> str:
    if not value:
        return "tampered"
    replacement = "A" if value[-1] != "A" else "B"
    return value[:-1] + replacement


def flip_hex(value: str) -> str:
    if not value:
        return "00"
    prefix = "0x" if value.startswith("0x") else ""
    body = value[2:] if prefix else value
    if not body:
        return prefix + "00"
    replacement = "0" if body[0].lower() != "0" else "1"
    return prefix + replacement + body[1:]


def flip_base64_bytes(value: str) -> str:
    try:
        raw = bytearray(base64.b64decode(value, validate=True))
    except Exception:
        return flip_text(value)
    if not raw:
        return base64.b64encode(b"tampered").decode("ascii")
    raw[0] ^= 0x01
    return base64.b64encode(bytes(raw)).decode("ascii")


def write_variant(output_dir: Path, name: str, payload: dict) -> Path:
    path = output_dir / f"{name}.json"
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


def post_payload(pre_base_url: str, payload_path: Path) -> tuple[int, str]:
    url = pre_base_url.rstrip("/") + "/api/v1/pghd/submit"
    body = payload_path.read_bytes()
    req = request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=30) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")
    except error.URLError as exc:
        raise SystemExit(f"[FAIL] Cannot reach PRE at {url}: {exc}") from exc


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create tampered PGHD submit payload fixtures for integrity testing."
    )
    parser.add_argument("payload", help="Path to a valid Android PGHD submit JSON payload.")
    parser.add_argument(
        "-o",
        "--output-dir",
        default="tmp/tampered-pghd",
        help="Directory for generated tampered payloads.",
    )
    parser.add_argument(
        "--pre-base-url",
        default=os.environ.get("PRE_BASE_URL", "http://127.0.0.1:4100"),
        help="PRE base URL used with --post. Defaults to PRE_BASE_URL or http://127.0.0.1:4100.",
    )
    parser.add_argument(
        "--post",
        action="store_true",
        help="Submit each tampered payload to PRE and print [PASS] if it is rejected.",
    )
    args = parser.parse_args()

    input_path = Path(args.payload)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    original = json.loads(input_path.read_text(encoding="utf-8"))
    required = ["enc_pghd", "h_cipher", "signature"]
    missing = [field for field in required if field not in original]
    if missing:
        raise SystemExit(f"Payload is missing required PGHD field(s): {', '.join(missing)}")

    variants = {}

    enc_variant = dict(original)
    enc_variant["enc_pghd"] = flip_text(str(enc_variant["enc_pghd"]))
    variants["tampered_enc_pghd"] = enc_variant

    hash_variant = dict(original)
    hash_variant["h_cipher"] = flip_hex(str(hash_variant["h_cipher"]))
    variants["tampered_h_cipher"] = hash_variant

    signature_variant = dict(original)
    signature_variant["signature"] = flip_base64_bytes(str(signature_variant["signature"]))
    variants["tampered_signature"] = signature_variant

    print("[INFO] Generated tampered PGHD payload fixtures:")
    paths = {}
    for name, payload in variants.items():
        path = write_variant(output_dir, name, payload)
        paths[name] = path
        print(f"[PASS] {name}: {path}")

    print()
    print("Manual PRE test example:")
    print("  PRE_BASE_URL=${PRE_BASE_URL:-http://127.0.0.1:4100}")
    for name in variants:
        print(
            f"  curl -sS -X POST \"$PRE_BASE_URL/api/v1/pghd/submit\" "
            f"-H 'Content-Type: application/json' --data-binary @{output_dir / (name + '.json')}"
        )
    print()
    print("Expected result: each tampered payload is rejected, or later invalidated on access.")

    if args.post:
        print()
        print(f"[INFO] Posting tampered payloads to {args.pre_base_url.rstrip('/')}/api/v1/pghd/submit")
        failed = False
        for name, path in paths.items():
            status, body = post_payload(args.pre_base_url, path)
            if 200 <= status <= 299:
                print(f"[FAIL] {name}: PRE accepted tampered payload unexpectedly. HTTP {status}: {body}")
                failed = True
            else:
                print(f"[PASS] {name}: PRE rejected tampered payload. HTTP {status}: {body}")
        if failed:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
