#!/bin/sh
URL=http://127.0.0.1:4000/
TIMEOUT=5
curl -m ${TIMEOUT} --connect-timeout ${TIMEOUT} -s --fail -o /dev/null ${URL}