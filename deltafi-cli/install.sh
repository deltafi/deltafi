#!/usr/bin/env bash
set -e
export DELTAFICLI_WORKDIR=$(cd $(dirname $(readlink -f $0)) && pwd)
export DELTAFI=${DELTAFICLI_WORKDIR}/deltafi
[[ -L /usr/local/bin/deltafi ]] && echo "Replacing existing deltafi CLI installation $(ls -la /usr/local/bin/deltafi)"
sudo ln -fs ${DELTAFI} /usr/local/bin || ln -fs ${DELTAFI} /usr/local/bin
if [[ ! -f ${DELTAFICLI_WORKDIR}/config ]]; then
    cp ${DELTAFICLI_WORKDIR}/config.template ${DELTAFICLI_WORKDIR}/config
    echo "Installing default config at ${DELTAFICLI_WORKDIR}/config"
fi
