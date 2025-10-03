FROM golang:1.25.1-alpine AS test
WORKDIR /app
# Install git for tests (some Go tools require it)
# RUN apk add --no-cache git gcc musl-dev
COPY . .
RUN go test -v ./...

FROM golang:1.25.1-alpine AS builder
WORKDIR /app
COPY . .
RUN go mod download
RUN CGO_ENABLED=0 GOOS=linux go build -o deltafi-dirwatcher cmd/deltafi-dirwatcher/main.go

FROM alpine:latest
WORKDIR /app
RUN apk --no-cache add ca-certificates \
    && apk upgrade -U --no-cache libssl3 libcrypto3 busybox coreutils

RUN mkdir -p /watched-dir && \
    adduser -u 1000 -D deltafi && \
    chown -R deltafi:deltafi /app /watched-dir
COPY --from=builder /app/deltafi-dirwatcher .

VOLUME ["/watched-dir"]

USER deltafi
ENTRYPOINT ["/app/deltafi-dirwatcher"]
