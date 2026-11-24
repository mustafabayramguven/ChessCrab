# ChessCrab sunucusu
FROM rust:1-slim-bookworm AS build
WORKDIR /src
COPY . .
RUN cargo build --release -p chesscrab-server -p chesscrab-engine --bins

FROM debian:bookworm-slim
ARG WITH_STOCKFISH=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && if [ "$WITH_STOCKFISH" = "1" ]; then apt-get install -y --no-install-recommends stockfish; fi \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/release/chesscrab-server /usr/local/bin/
COPY --from=build /src/target/release/crabfish /usr/local/bin/
ENV CHESSCRAB_ADDR=0.0.0.0:3437 \
    CHESSCRAB_DB=/data/chesscrab.db
RUN if [ -x /usr/games/stockfish ]; then echo "CHESSCRAB_STOCKFISH=/usr/games/stockfish" > /etc/chesscrab.env; fi
VOLUME /data
EXPOSE 3437
CMD ["sh", "-c", "[ -f /etc/chesscrab.env ] && export $(cat /etc/chesscrab.env); exec chesscrab-server"]
