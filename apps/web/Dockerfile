# syntax=docker/dockerfile:1.7

FROM node:20-bookworm-slim AS web-build
WORKDIR /src
COPY web/package.json web/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --no-audit --no-fund; fi
COPY web/ ./
RUN npm run build

FROM rust:1.83-bookworm AS server-build
WORKDIR /src
COPY server/Cargo.toml server/Cargo.lock* ./
RUN mkdir -p src \
 && echo 'fn main() {}' > src/main.rs \
 && cargo build --release \
 && rm -rf src
COPY server/ ./
RUN touch src/main.rs && cargo build --release

FROM debian:bookworm-slim AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /data /srv

ENV ERV_DATA_DIR=/data \
    ERV_STATIC_DIR=/srv/dist \
    ERV_PORT=3000 \
    ERV_BIND=0.0.0.0 \
    ERV_LOG=info \
    ERV_INSECURE_RELAY_TLS=1

COPY --from=web-build /src/dist /srv/dist
COPY --from=server-build /src/target/release/erv-web /usr/local/bin/erv-web

VOLUME ["/data"]
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -fsS http://127.0.0.1:3000/api/health || exit 1

ENTRYPOINT ["/usr/local/bin/erv-web"]
