FROM golang:1.26 AS builder
WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=1 go build -o deltafi-analytics cmd/collector/main.go

# Test stage
FROM builder AS test
RUN go test -v ./...

# Runtime stage - glibc 2.38+ (required by DuckDB)
FROM chainguard/glibc-dynamic:latest
WORKDIR /app

COPY --from=builder /app/deltafi-analytics .

VOLUME ["/data/analytics"]

USER 1000
EXPOSE 8080
ENTRYPOINT ["/app/deltafi-analytics"]
