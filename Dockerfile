# ChessCrab senkronizasyon sunucusu. Satranç hesabı (motor/analiz/dersler) telefonda yapılır.
FROM rust:1-slim-bookworm AS build
WORKDIR /src
COPY . .
RUN cargo build --release -p chesscrab-server

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/release/chesscrab-server /usr/local/bin/
ENV CHESSCRAB_ADDR=0.0.0.0:3437 \
    CHESSCRAB_DB=/data/chesscrab.db
VOLUME /data
EXPOSE 3437
CMD ["chesscrab-server"]
