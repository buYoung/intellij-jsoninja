#!/usr/bin/env bash
set -euo pipefail

rustup target add wasm32-wasip1
WASI_VERSION=33
WASI_SDK_NAME="wasi-sdk-${WASI_VERSION}.0-x86_64-linux"
WASI_SDK_ARCHIVE="${WASI_SDK_NAME}.tar.gz"
curl -fsSL "https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_VERSION}/${WASI_SDK_ARCHIVE}" -o "$RUNNER_TEMP/$WASI_SDK_ARCHIVE"
tar -xzf "$RUNNER_TEMP/$WASI_SDK_ARCHIVE" -C "$RUNNER_TEMP"
WASI_SDK_PATH="$RUNNER_TEMP/$WASI_SDK_NAME"
echo "CC_wasm32_wasip1=$WASI_SDK_PATH/bin/clang" >> "$GITHUB_ENV"
echo "AR_wasm32_wasip1=$WASI_SDK_PATH/bin/llvm-ar" >> "$GITHUB_ENV"
echo "WASI_SYSROOT=$WASI_SDK_PATH/share/wasi-sysroot" >> "$GITHUB_ENV"
echo "CFLAGS_wasm32_wasip1=--sysroot=$WASI_SDK_PATH/share/wasi-sysroot" >> "$GITHUB_ENV"
