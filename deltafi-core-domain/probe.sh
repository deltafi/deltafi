#!/bin/sh
URL=http://127.0.0.1:8080/graphql
TIMEOUT=5
QUERY="{lastCreated(last:1){did}}"
curl -m ${TIMEOUT} --connect-timeout ${TIMEOUT} -s --fail -o /dev/null \
  -H "Content-Type: application/json" -d "{ \"query\": \"${QUERY}\" }" ${URL}
