#!/usr/bin/env python3
import argparse
import base64
import json
import os
from pathlib import Path
from urllib import error, request


def flip_ciphertext(enc_pghd: str) -> str:
    raw = bytearray(base64.b64decode(enc_pghd, validate=True))
    if not raw:
        raise SystemExit("enc_pghd is empty")
    raw[0] ^= 0x01
    return base64.b64encode(bytes(raw)).decode("ascii")


def post_ipfs_add(ipfs_base_url: str, filename: str, content: bytes) -> tuple[int, str]:
    boundary = "----decmed-pghd-integrity-boundary"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: text/plain\r\n\r\n"
    ).encode("utf-8") + content + f"\r\n--{boundary}--\r\n".encode("utf-8")
    req = request.Request(
        ipfs_base_url.rstrip("/") + "/add",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=30) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")
    except error.URLError as exc:
        raise SystemExit(f"[FAIL] Cannot reach IPFS API at {ipfs_base_url.rstrip()}/add: {exc}") from exc


def extract_cid(body: str) -> str | None:
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        return None
    return parsed.get("cid") or parsed.get("Hash")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Create a tampered IPFS object from a valid PGHD submit payload. "
            "This does not mutate the original CID; IPFS content is immutable."
        )
    )
    parser.add_argument("payload", help="Path to a valid Android PGHD submit JSON payload.")
    parser.add_argument(
        "-o",
        "--output-dir",
        default="tmp/tampered-pghd-ipfs",
        help="Directory for generated tampered IPFS object fixture.",
    )
    parser.add_argument(
        "--ipfs-base-url",
        default=os.environ.get("IPFS_BASE_URL", "http://127.0.0.1:9094/api/v0"),
        help="IPFS API base URL used with --post. Defaults to IPFS_BASE_URL or http://127.0.0.1:9094/api/v0.",
    )
    parser.add_argument(
        "--post",
        action="store_true",
        help="Upload the tampered ciphertext object to IPFS and print the new CID.",
    )
    args = parser.parse_args()

    input_path = Path(args.payload)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    original = json.loads(input_path.read_text(encoding="utf-8"))
    for field in ("enc_pghd", "h_cipher", "signature", "batch_id"):
        if field not in original:
            raise SystemExit(f"Payload is missing required field: {field}")

    tampered_enc_pghd = flip_ciphertext(str(original["enc_pghd"]))
    object_path = output_dir / "tampered_ipfs_enc_pghd.txt"
    object_path.write_text(tampered_enc_pghd, encoding="utf-8")

    metadata_hint = {
        "batch_id": original["batch_id"],
        "expected_original_h_cipher": original["h_cipher"],
        "expected_original_signature": original["signature"],
        "tampered_ipfs_object_file": str(object_path),
        "test_expectation": (
            "If PGHD metadata points to this tampered object while keeping the original "
            "h_cipher/signature, PRE or hospital client must reject it with OUTER_HASH_MISMATCH "
            "or SIGNATURE_INVALID and then invalidate the PGHD entry."
        ),
    }
    hint_path = output_dir / "tampered_ipfs_metadata_hint.json"
    hint_path.write_text(json.dumps(metadata_hint, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print("[PASS] Created tampered IPFS PGHD object fixture:")
    print(f"       {object_path}")
    print("[PASS] Created metadata expectation hint:")
    print(f"       {hint_path}")
    print("[INFO] Original CID cannot be mutated in IPFS. Use this object as a new test CID in a controlled metadata fixture.")

    if args.post:
        status, body = post_ipfs_add(args.ipfs_base_url, object_path.name, object_path.read_bytes())
        if not (200 <= status <= 299):
            raise SystemExit(f"[FAIL] IPFS rejected tampered object. HTTP {status}: {body}")
        cid = extract_cid(body)
        if not cid:
            raise SystemExit(f"[FAIL] IPFS response did not contain cid/Hash. HTTP {status}: {body}")
        print(f"[PASS] Uploaded tampered IPFS object. CID: {cid}")
        print("[INFO] Expected access result after wiring this CID into test metadata: OUTER_HASH_MISMATCH or SIGNATURE_INVALID.")


if __name__ == "__main__":
    main()
