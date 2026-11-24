# ChessCrab: senkronizasyon sunucusu + yerelde (tarayıcıda) çalışan web uygulaması.
# Sunucu satranç hesabı yapmaz; motor/analiz/dersler WASM olarak istemcide çalışır.

# 1) Web uygulaması (WASM) — mimariden bağımsız, yerel platformda derlenir
FROM --platform=$BUILDPLATFORM rust:1-slim-bookworm AS web
RUN apt-get update && apt-get install -y --no-install-recommends pkg-config libssl-dev ca-certificates && rm -rf /var/lib/apt/lists/*
RUN rustup target add wasm32-unknown-unknown \
 && cargo install wasm-bindgen-cli --version 0.2.130 --locked
WORKDIR /src
COPY . .
RUN cargo build --release -p chesscrab-wasm --target wasm32-unknown-unknown \
 && wasm-bindgen --target web --out-dir web/pkg target/wasm32-unknown-unknown/release/chesscrab_wasm.wasm

# 2) Senkronizasyon sunucusu
FROM rust:1-slim-bookworm AS server
WORKDIR /src
COPY . .
RUN cargo build --release -p chesscrab-server

# 3) Çalışma imajı
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=server /src/target/release/chesscrab-server /usr/local/bin/
COPY --from=web /src/web /app/web
ENV CHESSCRAB_ADDR=0.0.0.0:3437 \
    CHESSCRAB_DB=/data/chesscrab.db \
    CHESSCRAB_WEB_DIR=/app/web
VOLUME /data
EXPOSE 3437
CMD ["chesscrab-server"]
