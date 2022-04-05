#!/bin/sh
if [[ -z $PROBE_DIR ]]; then
  echo "\$PROBE_DIR must be set"
  exit 1
fi
mkdir -p $PROBE_DIR
STALE_FILES="$(find $PROBE_DIR -type f -mmin +1)"
if [[ "$STALE_FILES" != "" ]]; then
  echo "Stale probe files:"
  echo $STALE_FILES
  exit 1
else
  exit 0
fi