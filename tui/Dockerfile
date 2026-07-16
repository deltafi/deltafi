# ABOUTME: Dockerfile for the DeltaFi TUI container with ttyd web terminal.
# ABOUTME: Built via Gradle: ./gradlew tui:docker

# Build stage
FROM golang:1.26-alpine AS builder

RUN apk add --no-cache git make

WORKDIR /build

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go mod download

# Copy TUI source files
COPY . .

# Generate GraphQL client (genqlient is pinned in go.mod)
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go run github.com/Khan/genqlient graphql/genqlient.yaml

# Build the TUI binary
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    VERSION=$(cat VERSION) && \
    CGO_ENABLED=0 go build -ldflags "-X main.Version=${VERSION} -w -s" -o /deltafi main.go

# Final stage
FROM alpine:latest

RUN apk add -U --no-cache \
    ttyd \
    bash \
    bash-completion \
    git \
    curl \
    tmux \
    vim \
    tree \
    jq \
    && apk upgrade -U --no-cache

# Copy VERSION file
COPY VERSION /deltafi/VERSION
COPY .tmux.* /deltafi

# Copy orchestration directory
COPY orchestration /deltafi/orchestration
COPY deltafi-docs/docs /deltafi/docs

# Create directories and move orchestration to versioned path
RUN VERSION=$(cat /deltafi/VERSION) && \
    mkdir -p /deltafi/data /deltafi/repos /deltafi/site /deltafi/.deltafi /deltafi/${VERSION} && \
    mv /deltafi/orchestration /deltafi/${VERSION}/orchestration && \
    chmod -R a+rwX /deltafi && \
    printf 'export PATH=/deltafi:$PATH\nexport PS1='"'"'\\[\\e[36m\\]Δ\\[\\e[0m\\] \\w \\$ '"'"'\n' > /etc/profile.d/deltafi.sh

# Copy the TUI binary
COPY --from=builder /deltafi /deltafi/deltafi

# Generate and install deltafi bash completion
RUN DELTAFI_WEBTERMINAL=true /deltafi/deltafi completion bash > /usr/share/bash-completion/completions/deltafi

# Create entrypoint script
RUN cat > /entrypoint.sh << 'EOF'
#!/bin/bash
exec ttyd -W -p 7681 --base-path /shell/ tmux -u -2 new -A -s DeltaFi -n 'Web Terminal' bash -l
EOF
RUN chmod +x /entrypoint.sh

# Set working directory
WORKDIR /deltafi

# Expose ttyd port
EXPOSE 7681

# Run config on startup, then start ttyd
ENTRYPOINT ["/entrypoint.sh"]
