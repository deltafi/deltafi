#!/bin/bash

echo
echo "This script will apply the following node labels to all nodes in the cluster:"
echo
echo "  node-role.deltafi.org/compute=true"
echo "  node-role.deltafi.org/storage=true"
echo

read -r -p "Are you sure you want to do this? [y/N] " response
case "$response" in
  [yY][eE][sS]|[yY])
    for NODE in $(kubectl get nodes | grep -v NAME | awk '{print $1}'); do
      kubectl label nodes $NODE node-role.deltafi.org/compute=true node-role.deltafi.org/storage=true --overwrite=true
    done
    ;;
  *)
    echo "Aborting"
    exit 0
    ;;
esac
