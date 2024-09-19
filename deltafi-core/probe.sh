#!/bin/sh
URL=http://127.0.0.1:${SERVER_PORT:-8080}/graphql
TIMEOUT=5
curl -m ${TIMEOUT} --connect-timeout ${TIMEOUT} -H "Content-Type: application/json" -s -o /dev/null ${URL}
