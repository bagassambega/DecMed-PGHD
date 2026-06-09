# Build stage
FROM rust:1.87-slim AS build

ARG APP_NAME=proxy_reencryption

WORKDIR /build

# Install build dependencies
RUN apt-get update && apt-get install -y \
    make \
    perl \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy only Cargo files first (for better caching)
COPY Cargo.toml Cargo.lock ./

# Create dummy src to cache dependencies
RUN mkdir src && \
    echo "fn main() {}" > src/main.rs && \
    cargo build --release --locked 2>&1 | grep -v "warning:" || true

# Copy actual source code
RUN rm -rf src
COPY src ./src

# Build actual application
RUN cargo clean && cargo build --release --locked

# Copy binary to /bin
RUN cp ./target/release/$APP_NAME /bin/server

# Runtime stage - use a Debian release with glibc compatible with rust:1.87-slim.
FROM debian:bookworm-slim AS final

RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /bin/server /bin/

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD /bin/server --health-check || exit 1

CMD ["/bin/server"]
