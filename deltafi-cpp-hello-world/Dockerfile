# ABOUTME: Multi-stage build for the DeltaFi C++ Hello World plugin.
# ABOUTME: Builds a static binary and packages it in a distroless container.

FROM alpine:3.21 AS builder
RUN apk add --no-cache cmake make ninja linux-headers g++ git
WORKDIR /build
COPY deltafi-cpp-action-kit/ ./deltafi-cpp-action-kit/
COPY deltafi-cpp-hello-world/ ./deltafi-cpp-hello-world/
WORKDIR /build/deltafi-cpp-hello-world

ARG VERSION=0.0.0-dev
RUN cmake -B build -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_EXE_LINKER_FLAGS="-static" \
    -DVERSION="${VERSION}" \
    -DDELTAFI_ACTION_KIT_PATH=/build/deltafi-cpp-action-kit \
    -DHELLO_WORLD_TESTS=OFF
RUN cmake --build build -j

FROM alpine:3.21 AS tester
RUN apk add --no-cache cmake make ninja linux-headers g++ git
WORKDIR /build
COPY deltafi-cpp-action-kit/ ./deltafi-cpp-action-kit/
COPY deltafi-cpp-hello-world/ ./deltafi-cpp-hello-world/
WORKDIR /build/deltafi-cpp-hello-world

ARG VERSION=0.0.0-dev
RUN cmake -B build -G Ninja \
    -DVERSION="${VERSION}" \
    -DDELTAFI_ACTION_KIT_PATH=/build/deltafi-cpp-action-kit && \
    cmake --build build -j && \
    ctest --test-dir build --output-on-failure

FROM gcr.io/distroless/static:nonroot
ENV ACTION_THREADS=1
ENV ACTIONS_ACTIONTHREADS_ORG_DELTAFI_CPP_HELLO_WORLD_HELLOWORLDCPPTRANSFORMACTION=2
ENV TABLE_LOOKUP_CACHE_TTL=10s
ENV TABLE_LOOKUP_FAILURE_CACHE_TTL=1s
ENV ARTIFACT_CACHE_TTL=10s
ENV ARTIFACT_FAILURE_CACHE_TTL=1s
COPY --from=builder /build/deltafi-cpp-hello-world/build/deltafi-cpp-hello-world /app/deltafi-plugin
COPY --from=builder /build/deltafi-cpp-hello-world/flows /app/flows
COPY --from=builder /build/deltafi-cpp-hello-world/integration /app/integration
WORKDIR /app
ENTRYPOINT ["/app/deltafi-plugin"]
