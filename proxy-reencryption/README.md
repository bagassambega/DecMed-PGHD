# 🔐 Proxy Re-encryption Backend

Backend Rust/Actix untuk DecMed yang menangani:

- Proxy re-encryption logic
- IPFS file upload/download coordination
- IOTA smart contract calls
- Gas station integration

## 🚀 Quick Start

### **Local Development (Recommended)**

```bash
# Start Redis in a separate terminal without Docker.
# Native redis-server listens on 6379 by default.
redis-server --requirepass password

# Build lokal dengan Cargo
cargo build --release

# Jalankan
cargo run --release

# Backend akan listen di http://127.0.0.1:4000
```

### **Using Helper Script**

```bash
# Local build & run
chmod +x build.sh
./build.sh local

# Docker build (inside container)
./build.sh docker

# Docker minimal (using pre-built binary)
./build.sh docker-minimal
```

---

## 🐳 Docker Options

### **Option 1: Local Build (Fastest for Development)**

```bash
redis-server --requirepass password
cargo build --release
cargo run --release
```

✅ **Pros:**

- Fastest build times with Cargo cache
- Easy debugging
- No Docker overhead

❌ **Cons:**

- Requires Rust installed locally

---

### **Option 2: Docker Full Build**

```bash
# Stop any running container
docker-compose down

# Build in Docker
DOCKER_BUILDKIT=1 docker-compose build --no-cache

# Run
docker-compose up
```

✅ **Pros:**

- Isolated build environment
- Reproducible builds

❌ **Cons:**

- Slower build times
- Large downloads of dependencies

⏱️ **Estimated time:** 10-15 minutes on first build

---

### **Option 3: Docker Minimal Build** (Recommended if want Docker)

Build locally first:

```bash
cargo build --release
```

Then create minimal Docker image:

```bash
docker build -f Dockerfile.minimal -t decmed-proxy:minimal .
docker run -p 8000:8000 decmed-proxy:minimal
```

✅ **Pros:**

- Docker benefits with local build speed
- Minimal image size (~50MB)
- Fast container startup

⏱️ **Estimated time:** 1 minute (after local cargo build)

---

## 🔧 Environment Variables

```bash
# VPS Endpoints (Update if changed)
IOTA_RPC_URL=http://103.107.4.68:9000
IPFS_API_BASE_URL=http://103.107.4.68:9094/api/v0
IPFS_GATEWAY_BASE_URL=http://103.107.4.68:8080
GAS_STATION_BASE_URL=http://103.107.4.68:9527/v1

# Logging
RUST_LOG=debug

# Backend server
BACKEND_PORT=8000
```

Create `.env` file in this directory with above variables (optional, uses defaults if not provided).

---

## 🧪 Testing

### **Health Check**

```bash
curl http://127.0.0.1:8000/health
```

### **Check VPS Connection**

```bash
curl -X POST http://127.0.0.1:8000/api/status
```

### **Upload File to IPFS**

```bash
# Create test file
echo "test data" > test.txt

# Upload via backend
curl -X POST http://127.0.0.1:8000/api/ipfs/upload \
  -F "file=@test.txt"

# Response should contain CID
```

---

## 🐛 Troubleshooting

### **Build Stuck on Cargo Dependencies**

**Problem**: Docker build stuck updating git/crates

**Solutions**:

1. **Recommended**: Use local build instead

   ```bash
   cargo build --release
   cargo run --release
   ```

2. **Or**: Use minimal Docker (build locally first)

   ```bash
   cargo build --release
   docker build -f Dockerfile.minimal -t decmed-proxy .
   docker run -p 8000:8000 decmed-proxy
   ```

3. **Or**: Increase timeout and rebuild

   ```bash
   docker-compose down
   DOCKER_BUILDKIT=1 docker-compose build --no-cache
   docker-compose up
   ```

---

### **Backend Connection Errors**

**Problem**: Backend starts but cannot connect to VPS

**Solution**:

```bash
# Check VPS endpoints accessible
curl http://103.107.4.68:9000 -I
curl http://103.107.4.68:8080 -I
curl http://103.107.4.68:9094/api/v0/version

# If timeout: Check internet/firewall
# If need: Setup SSH tunnel
ssh -L 9000:127.0.0.1:9000 \
    -L 8080:127.0.0.1:8080 \
    -L 9094:127.0.0.1:9094 \
    -L 9527:127.0.0.1:9527 \
    -N -f ubuntu@103.107.4.68
```

---

### **Port 8000 Already in Use**

```bash
# Find what's using port 8000
lsof -i :8000

# Kill process
kill -9 <PID>

# Or use different port
BACKEND_PORT=8001 cargo run --release
```

---

### **Cargo Build Errors**

```bash
# Update Rust
rustup update

# Clean and rebuild
cargo clean
cargo build --release

# Check specific error
cargo build --release 2>&1 | grep -A 5 error
```

---

## 📚 Project Structure

```
proxy-reencryption/
├── src/
│   ├── main.rs           - Entry point
│   ├── constants.rs      - Configuration constants
│   ├── handlers.rs       - HTTP request handlers
│   ├── middlewares.rs    - Custom middlewares
│   ├── utils.rs          - Utility functions
│   ├── types.rs          - Custom types
│   ├── proxy_error.rs    - Error types
│   └── ...
├── Cargo.toml           - Dependencies
├── Dockerfile           - Full build (with cargo)
├── Dockerfile.minimal   - Minimal image (uses pre-built binary)
├── docker-compose.yml   - Docker Compose config
├── build.sh            - Helper build script
└── README.md           - This file
```

---

## 📖 API Endpoints

See main README.md for complete API documentation.

Common endpoints:

- `GET /health` - Health check
- `POST /api/status` - Backend status
- `POST /api/ipfs/upload` - Upload file to IPFS
- `GET /api/ipfs/download/<cid>` - Download from IPFS
- `POST /api/iota/call` - Call smart contract

---

## 🔗 Related Documentation

- [Main README](../README.md) - Full system documentation
- [VPS.md](../VPS.md) - VPS infrastructure details
- [IOTA.md](../IOTA.md) - IOTA node setup

---

**Last Updated:** May 2026
