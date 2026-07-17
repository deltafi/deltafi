#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

# ABOUTME: Builder base image for the DeltaFi C++ action kit: toolchain plus the kit source baked in.
# ABOUTME: BUILD_FLAVOR picks glibc (debian-trixie) or musl (alpine); plugins build FROM this image.

# BUILD_FLAVOR selects the libc / toolchain:
#   glibc (default) - debian:trixie-slim (g++-14 provides <format> for spdlog's SPDLOG_USE_STD_FORMAT)
#   musl            - alpine:3.24 with the static toolchain
ARG BUILD_FLAVOR=glibc

# --- musl toolchain: static build on Alpine ---
FROM alpine:3.24 AS base-musl
RUN apk add --no-cache cmake make ninja linux-headers g++ git openssl openssl-dev openssl-libs-static && \
    apk upgrade --no-cache -U
ENV DELTAFI_CMAKE_LINK_FLAGS="-DCMAKE_EXE_LINKER_FLAGS=-static -DOPENSSL_USE_STATIC_LIBS=TRUE"

# --- glibc toolchain: dynamic build on Debian trixie ---
FROM debian:trixie-slim AS base-glibc
RUN apt-get update && apt-get install -y --no-install-recommends \
        cmake ninja-build g++ git libssl-dev ca-certificates \
    && apt-get update \
    && rm -rf /var/lib/apt/lists/*
ENV DELTAFI_CMAKE_LINK_FLAGS=""

# --- Bake the kit source so downstream plugin builds resolve it locally (no GitLab fetch) ---
FROM base-${BUILD_FLAVOR}
ARG VERSION=0.0.0-dev
COPY . /opt/deltafi-cpp-action-kit/
RUN echo "${VERSION}" > /opt/deltafi-cpp-action-kit/VERSION
ENV DELTAFI_ACTION_KIT_PATH=/opt/deltafi-cpp-action-kit
LABEL org.opencontainers.image.title="deltafi-cpp-action-kit" \
      org.opencontainers.image.description="DeltaFi C++ action kit builder base (toolchain + kit source)" \
      org.opencontainers.image.version="${VERSION}"
