#!/bin/bash
# ABOUTME: Runs UI e2e tests with the mock dev server.
# ABOUTME: Starts Vite dev server with mocks, waits for ready, runs Cypress, ensures cleanup.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PORT=8080
STARTED_SERVER=false
SERVER_PID=""
BASE_URL="http://localhost:$PORT"
ENTRYPOINT_URL="$BASE_URL/src/main.ts"
SERVER_MATCH="vite serve --mode test"

has_required_test_deps() {
  node <<'NODE' >/dev/null 2>&1
const fs = require("fs");
const path = require("path");

const pkgPath = path.join(process.cwd(), "node_modules", "msw", "package.json");
if (!fs.existsSync(pkgPath)) {
  process.exit(1);
}

const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));
const exportsField = pkg.exports || {};

if (!exportsField["./browser"]) {
  process.exit(1);
}
NODE
}

ensure_test_deps() {
  if has_required_test_deps; then
    return
  fi

  echo "Refreshing UI dependencies..."
  npm install

  if ! has_required_test_deps; then
    echo "ERROR: UI dependencies are still missing required test packages after npm install." >&2
    exit 1
  fi
}

listening_pid() {
  lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n 1
}

cleanup() {
  if [ "$STARTED_SERVER" = true ] && [ -n "$SERVER_PID" ]; then
    echo "Cleaning up..."
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT

ensure_test_deps

EXISTING_PID="$(listening_pid)"

if [ -n "$EXISTING_PID" ]; then
  if ps -p "$EXISTING_PID" -o command= | grep -Fq "$SERVER_MATCH"; then
    echo "Using existing test server on port $PORT (pid $EXISTING_PID)..."
  else
    echo "ERROR: Port $PORT is already in use by a non-test server process (pid $EXISTING_PID)." >&2
    echo "Stop that process or free the port before running UI tests." >&2
    exit 1
  fi
else
  echo "Starting test server on port $PORT..."
  npm run test-server &
  SERVER_PID=$!
  STARTED_SERVER=true

  echo "Waiting for server to be ready..."
  for i in {1..60}; do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "ERROR: Test server exited before becoming ready." >&2
      exit 1
    fi

    if curl -fsS -o /dev/null "$BASE_URL/" 2>/dev/null && curl -fsS -o /dev/null "$ENTRYPOINT_URL" 2>/dev/null; then
      echo "Server is ready."
      break
    fi
    if [ $i -eq 60 ]; then
      echo "ERROR: Server failed to start within 60 seconds."
      exit 1
    fi
    sleep 1
  done
fi

echo "Running Cypress tests..."
npm run test -- --config video=false "$@"
