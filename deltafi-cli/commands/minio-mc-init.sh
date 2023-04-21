#!/bin/sh
set -e
mc alias set deltafi http://deltafi-minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
/bin/bash