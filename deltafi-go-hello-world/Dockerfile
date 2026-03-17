FROM golang:1.26-alpine AS builder

WORKDIR /build

# Copy go.mod/go.sum files first for dependency layer caching.
# This layer only rebuilds when dependencies change, not on code changes.
COPY deltafi-go-action-kit/go.mod deltafi-go-action-kit/go.sum ./deltafi-go-action-kit/
COPY deltafi-go-hello-world/go.mod deltafi-go-hello-world/go.sum ./deltafi-go-hello-world/

WORKDIR /build/deltafi-go-hello-world

RUN go mod download -x

WORKDIR /build
COPY deltafi-go-action-kit/ ./deltafi-go-action-kit/
COPY deltafi-go-hello-world/ ./deltafi-go-hello-world/

WORKDIR /build/deltafi-go-hello-world
ARG VERSION=0.0.0-dev
ARG DESCRIPTION=Go Hello World reference plugin for DeltaFi
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags "\
  -X main.version=${VERSION} \
  -X 'main.description=${DESCRIPTION}' \
  -X gitlab.com/deltafi/deltafi/deltafi-go-action-kit.Version=${VERSION}" \
  -o deltafi-go-hello-world .

FROM alpine:3.23
ENV ACTION_THREADS=1
ENV ACTIONS_ACTIONTHREADS_ORG_DELTAFI_GO_HELLO_WORLD_HELLOWORLDGOTRANSFORMACTION=2
ENV TABLE_LOOKUP_CACHE_TTL=10s
ENV TABLE_LOOKUP_FAILURE_CACHE_TTL=1s
ENV ARTIFACT_CACHE_TTL=10s
ENV ARTIFACT_FAILURE_CACHE_TTL=1s
RUN apk --no-cache add ca-certificates
WORKDIR /app
COPY --from=builder /build/deltafi-go-hello-world/deltafi-go-hello-world deltafi-plugin
COPY --from=builder /build/deltafi-go-hello-world/flows ./flows
COPY --from=builder /build/deltafi-go-hello-world/integration ./integration
RUN chmod +x deltafi-plugin
ENTRYPOINT ["/app/deltafi-plugin"]
