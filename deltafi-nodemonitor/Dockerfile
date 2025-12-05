FROM alpine:latest AS docker-src

# Install bash, curl, ca-certificates, and jq (for JSON parsing)
RUN apk add --no-cache bash curl ca-certificates jq

# https://docs.docker.com/engine/release-notes/28/
ARG DOCKER_VERSION=29.1.2

# Download and install the latest Docker CLI static binary for the current architecture
RUN set -e; \
    # Detect architecture and map to Docker's naming convention
    ARCH=$(uname -m); \
    case "$ARCH" in \
        x86_64) DOCKER_ARCH="x86_64" ;; \
        aarch64) DOCKER_ARCH="aarch64" ;; \
        *) echo "Unsupported architecture: $ARCH" && exit 1 ;; \
    esac; \
    echo "Detected architecture: $ARCH -> Docker architecture: $DOCKER_ARCH"; \
    \
    echo "Docker version: $DOCKER_VERSION"; \
    \
    # Download and extract the Docker CLI binary
    curl -fsSL "https://download.docker.com/linux/static/stable/${DOCKER_ARCH}/docker-${DOCKER_VERSION}.tgz" | \
    tar -xzv -C /usr/local/bin --strip-components=1 docker/docker; \
    \
    # Make it executable
    chmod +x /usr/local/bin/docker

# Verify installations
RUN docker --version

FROM alpine:3.23 AS target

RUN apk -U --no-cache add \
  bash \
  curl \
  bc \
  netcat-openbsd \
  postgresql-client \
  && apk -U --no-cache upgrade

COPY --chmod=755 --from=docker-src /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app

COPY . /app
RUN adduser -S deltafi && docker --version && which docker && docker --version
USER deltafi

CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
