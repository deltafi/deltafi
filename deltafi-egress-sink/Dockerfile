FROM golang:alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o egress-sink-server .

FROM alpine:latest
WORKDIR /app
COPY probe.sh /
RUN apk --no-cache add curl
RUN mkdir -p /data/deltafi/out && \
    adduser -u 1000 -D egressinator && \
    chown -R 1000 /app
COPY --from=builder /app/egress-sink-server .
USER 1000
EXPOSE 9292
CMD ["./egress-sink-server"]