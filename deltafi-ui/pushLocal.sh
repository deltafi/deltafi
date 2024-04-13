#!/usr/bin/env bash

docker build -t localhost:5000/deltafi-ui:latest . && cluster loc bounce deltafi-ui
