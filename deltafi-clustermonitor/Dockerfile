FROM golang:1.26 AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o deltafi-clustermonitor .

FROM builder AS test
RUN go test -v ./...

FROM chainguard/static:latest
WORKDIR /app
COPY --from=builder /app/deltafi-clustermonitor .
USER 65532
ENTRYPOINT ["/app/deltafi-clustermonitor"]
