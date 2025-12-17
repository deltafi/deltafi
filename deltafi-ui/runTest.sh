#!/bin/bash
# ABOUTME: Runs UI e2e tests with the mock test server.
# ABOUTME: Starts server, waits for ready, runs Cypress, ensures cleanup.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PORT=8080
STARTED_SERVER=false

cleanup() {
  if [ "$STARTED_SERVER" = true ]; then
    echo "Cleaning up..."
    pkill -f "vite serve --mode test" 2>/dev/null || true
  fi
}

trap cleanup EXIT

# Check if server is already running on the port
if curl -s -o /dev/null "http://localhost:$PORT/" 2>/dev/null; then
  echo "Using existing server on port $PORT..."
else
  echo "Starting test server on port $PORT..."
  VITE_MOCK_REQUEST_DELAY=1 VITE_MOCK_RESPONSES=successResponse npx vite serve --mode test &
  STARTED_SERVER=true

  echo "Waiting for server to be ready..."
  for i in {1..30}; do
    if curl -s -o /dev/null "http://localhost:$PORT/" 2>/dev/null; then
      echo "Server is ready."
      break
    fi
    if [ $i -eq 30 ]; then
      echo "ERROR: Server failed to start within 30 seconds."
      exit 1
    fi
    sleep 1
  done
fi

echo "Running Cypress tests..."
npm run test -- --config video=false
