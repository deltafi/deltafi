#!/bin/sh
URL=http://127.0.0.1:9292/probe
TIMEOUT=5
curl -m ${TIMEOUT} --connect-timeout ${TIMEOUT} -s -o /dev/null ${URL}