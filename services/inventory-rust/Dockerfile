FROM rust:alpine as builder
WORKDIR /app
RUN apk add --no-cache musl-dev pkgconfig openssl-dev
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release

FROM alpine:latest
RUN apk add --no-cache libgcc
COPY --from=builder /app/target/release/inventory-rust /usr/local/bin/inventory-rust
EXPOSE 8085
CMD ["/usr/local/bin/inventory-rust"]
