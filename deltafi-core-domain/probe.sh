#!/bin/sh
URL=http://127.0.0.1:8080/graphql
TIMEOUT=5
curl -m ${TIMEOUT} --connect-timeout ${TIMEOUT} -s -o /dev/null ${URL}
