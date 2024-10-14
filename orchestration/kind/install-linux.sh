#!/bin/bash

# Prerequisites:
# git
# docker
# helm
# kubernetes CLI (kubectl)
# kubens

# yq
sudo wget -q https://github.com/mikefarah/yq/releases/download/v4.27.5/yq_linux_amd64 -O /usr/bin/yq && sudo chmod +x /usr/bin/yq

# kind
sudo wget -q https://kind.sigs.k8s.io/dl/v0.15.0/kind-linux-amd64 -O /usr/local/bin/kind && sudo chmod +x /usr/local/bin/kind
