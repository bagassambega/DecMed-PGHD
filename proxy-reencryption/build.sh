#!/bin/bash

# DecMed Proxy Backend - Build & Run Script
# Handles both local cargo build and docker build

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
BACKEND_DIR="$SCRIPT_DIR"

echo "🔧 DecMed Proxy Re-encryption Backend - Build & Run"
echo "=================================================="
echo ""

# Parse arguments
BUILD_MODE="${1:-local}"  # local, docker, or docker-minimal

case "$BUILD_MODE" in
  local)
    echo "📦 Mode: Local Cargo Build"
    echo ""
    
    cd "$BACKEND_DIR"
    
    echo "1️⃣  Building with Cargo (release mode)..."
    cargo build --release
    
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "🚀 Starting backend..."
    cargo run --release
    ;;
    
  docker)
    echo "🐳 Mode: Docker Build (with cargo inside)"
    echo ""
    
    cd "$BACKEND_DIR"
    
    echo "1️⃣  Cleaning up old containers..."
    docker-compose down 2>/dev/null || true
    
    echo "2️⃣  Building Docker image (with BUILDKIT)..."
    DOCKER_BUILDKIT=1 docker-compose build --no-cache
    
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "🚀 Starting backend in container..."
    docker-compose up
    ;;
    
  docker-minimal)
    echo "🐳 Mode: Docker Minimal (using pre-built local binary)"
    echo ""
    
    cd "$BACKEND_DIR"
    
    # Check if binary exists
    if [ ! -f "target/release/proxy_reencryption" ]; then
      echo "❌ Error: Binary not found at target/release/proxy_reencryption"
      echo ""
      echo "Please build locally first:"
      echo "  cargo build --release"
      exit 1
    fi
    
    echo "1️⃣  Building minimal Docker image..."
    docker build -f Dockerfile.minimal -t decmed-proxy:minimal .
    
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "🚀 Starting backend container..."
    docker run \
      --rm \
      -p 8000:8000 \
      -e RUST_LOG=debug \
      -e IOTA_RPC_URL="http://103.107.4.68:9000" \
      -e IPFS_API_BASE_URL="http://103.107.4.68:9094/api/v0" \
      -e IPFS_GATEWAY_BASE_URL="http://103.107.4.68:8080" \
      -e GAS_STATION_BASE_URL="http://103.107.4.68:9527/v1" \
      decmed-proxy:minimal
    ;;
    
  *)
    echo "❌ Invalid mode: $BUILD_MODE"
    echo ""
    echo "Usage: $0 [mode]"
    echo ""
    echo "Modes:"
    echo "  local          - Build & run locally with Cargo (recommended for development)"
    echo "  docker         - Build inside Docker container (slower, full build)"
    echo "  docker-minimal - Build Docker image with pre-built local binary (fastest docker)"
    echo ""
    echo "Examples:"
    echo "  $0 local"
    echo "  $0 docker"
    echo "  $0 docker-minimal"
    exit 1
    ;;
esac
