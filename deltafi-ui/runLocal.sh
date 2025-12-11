#!/bin/bash
SCHEME=http DELTAFI_DOMAIN=local.deltafi.org npm --prefix "$(dirname "$0")" run dev
