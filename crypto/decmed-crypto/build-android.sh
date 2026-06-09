#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CRATE_DIR="$ROOT_DIR/crypto/decmed-crypto"
ANDROID_APP_DIR="$ROOT_DIR/client/pghd-android/app"
JNI_LIBS_DIR="$ANDROID_APP_DIR/src/main/jniLibs"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_NDK_HOME must point to an installed Android NDK." >&2
  exit 1
fi

TARGETS=(
  "aarch64-linux-android:arm64-v8a:aarch64-linux-android30-clang"
  "x86_64-linux-android:x86_64:x86_64-linux-android30-clang"
)

for target_pair in "${TARGETS[@]}"; do
  IFS=":" read -r rust_target abi clang_name <<< "$target_pair"
  linker="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/$clang_name"
  if [[ ! -x "$linker" ]]; then
    echo "Android linker not found: $linker" >&2
    exit 1
  fi

  linker_env_name="CARGO_TARGET_${rust_target^^}_LINKER"
  linker_env_name="${linker_env_name//-/_}"
  rustflags_env_name="CARGO_TARGET_${rust_target^^}_RUSTFLAGS"
  rustflags_env_name="${rustflags_env_name//-/_}"
  env "$linker_env_name=$linker" \
    "$rustflags_env_name=-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384" \
    "CC_$rust_target=$linker" \
    cargo build --manifest-path "$CRATE_DIR/Cargo.toml" --release --target "$rust_target"
  mkdir -p "$JNI_LIBS_DIR/$abi"
  cp "$CRATE_DIR/target/$rust_target/release/libdecmed_crypto.so" "$JNI_LIBS_DIR/$abi/"
done

echo "Packaged libdecmed_crypto.so into $JNI_LIBS_DIR"
