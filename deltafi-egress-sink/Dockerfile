FROM golang:alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o egress-sink-server .

FROM alpine:latest
WORKDIR /app
COPY probe.sh /

RUN apk -U --no-cache add curl \
    && apk upgrade -U --no-cache libexpat libcrypto3 libssl3 busybox coreutils

RUN mkdir -p /data/deltafi/out && \
    adduser -u 1000 -D egressinator && \
    chown -R 1000 /app
COPY --from=builder /app/egress-sink-server .
USER 1000
EXPOSE 80
CMD ["./egress-sink-server"]
