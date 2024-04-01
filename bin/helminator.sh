#!/bin/sh

GIT_ROOT=$(git rev-parse --show-toplevel)
cd "$GIT_ROOT/charts/deltafi" || exit 1
rm -rf Chart.lock
rm -rf charts/*.tgz
helm dependency build
