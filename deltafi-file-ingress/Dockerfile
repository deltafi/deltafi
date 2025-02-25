FROM golang:alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o file-ingresser .

FROM alpine:latest
WORKDIR /app
RUN apk --no-cache add curl
RUN mkdir -p /data/deltafi/out && \
    adduser -u 1000 -D ingressinator && \
    chown -R 1000 /app
COPY --from=builder /app/file-ingresser .
USER 1000
CMD ["./file-ingress"]
