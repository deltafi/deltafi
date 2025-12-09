FROM golang:1.25 AS builder
WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=1 go build -o deltafi-analytics cmd/collector/main.go

# Test stage
FROM builder AS test
RUN go test -v ./...

# Runtime stage - use Debian trixie for glibc 2.38+ compatibility (required by DuckDB)
FROM debian:trixie-slim
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    wget \
    && apt upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && apt clean

RUN mkdir -p /data/analytics && \
    useradd -u 1000 -m deltafi && \
    chown -R deltafi:deltafi /app /data/analytics

COPY --from=builder /app/deltafi-analytics .

VOLUME ["/data/analytics"]

USER deltafi
EXPOSE 8080
ENTRYPOINT ["/app/deltafi-analytics"]
