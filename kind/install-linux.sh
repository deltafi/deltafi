#!/bin/bash

# Prerequisites:
# git
# docker
# helm
# kubernetes CLI (kubectl)
# kubens

# kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.15.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
