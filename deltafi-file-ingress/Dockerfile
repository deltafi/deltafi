FROM golang:1.24.5-alpine AS test
WORKDIR /app
# Install git for tests (some Go tools require it)
# RUN apk add --no-cache git gcc musl-dev
COPY . .
RUN go test -v ./...

FROM golang:1.24.5-alpine AS builder
WORKDIR /app
COPY . .
RUN go mod download
RUN CGO_ENABLED=0 GOOS=linux go build -o deltafi-file-ingress cmd/deltafi-file-ingress/main.go

FROM alpine:latest
WORKDIR /app
RUN apk --no-cache add ca-certificates
RUN mkdir -p /watched-dir && \
    adduser -u 1000 -D deltafi && \
    chown -R deltafi:deltafi /app /watched-dir
COPY --from=builder /app/deltafi-file-ingress .

VOLUME ["/watched-dir"]

USER deltafi
ENTRYPOINT ["/app/deltafi-file-ingress"]
