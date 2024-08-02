#!/bin/bash
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# This is the install script for bootstrapping or upgrading an air-gapped DeltaFi system.

# _readlink()
#
# Usage:
#   _readlink [-e|-f|<options>] <path/to/symlink>
#
# Options:
#   -f  All but the last component must exist.
#   -e  All components must exist.
#
# Description:
#   Wrapper for `readlink` that provides portable versions of GNU `readlink -f`
#   and `readlink -e`, which canonicalize by following every symlink in every
#   component of the given name recursively.
#
# More Information:
#   http://stackoverflow.com/a/1116890
_readlink() {
  local _target_path
  local _target_file
  local _final_directory
  local _final_path
  local _option

  for __arg in "${@:-}"
  do
    case "${__arg}" in
      -e|-f)
        _option="${__arg}"
        ;;
      -*)
        # do nothing
        # ':' is bash no-op
        :
        ;;
      *)
        if [[ -z "${_target_path:-}" ]]
        then
          _target_path="${__arg}"
        fi
        ;;
    esac
  done

  if [[ -z "${_option}" ]]
  then
    readlink "${@}"
  else
    if [[ -z "${_target_path:-}" ]]
    then
      printf "_readlink: missing operand\\n"
      return 1
    fi

    cd "$(dirname "${_target_path}")" || return 1
    _target_file="$(basename "${_target_path}")"

    # Iterate down a (possible) chain of symlinks
    while [[ -L "${_target_file}" ]]
    do
      _target_file="$(readlink "${_target_file}")"
      cd "$(dirname "${_target_file}")" || return 1
      _target_file="$(basename "${_target_file}")"
    done

    # Compute the canonicalized name by finding the physical path
    # for the directory we're in and appending the target file.
    _final_directory="$(pwd -P)"
    _final_path="${_final_directory}/${_target_file}"

    if [[ "${_option}" == "-f" ]]
    then
      printf "%s\\n" "${_final_path}"
      return 0
    elif [[ "${_option}" == "-e" ]]
    then
      if [[ -e "${_final_path}" ]]
      then
        printf "%s\\n" "${_final_path}"
        return 0
      else
        return 1
      fi
    else
      return 1
    fi
  fi
}

_confirm() {
  local prompt=${1:-"Are you sure?"}
  read -r -p "$prompt [y/N] " response
  case "$response" in
    [yY][eE][sS]|[yY])
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}


DELTAFI_PATH="$( cd "$( dirname "$(_readlink -f "${BASH_SOURCE[0]}")")" &> /dev/null && pwd )"
export DELTAFI_PATH
PLUGIN_LIST_FILE="${DELTAFI_PATH}/airgap.plugin.manifest"
SNAPSHOT_FILE="${DELTAFI_PATH}/airgap.snapshot.json"
DELTAFI_VERSION=$(cat "${DELTAFI_PATH}/VERSION")

export PATH=$DELTAFI_PATH/deltafi-cli:$PATH:$DELTAFI_PATH/bin

pushd "$DELTAFI_PATH" > /dev/null || exit

DELTAFI_CLI=${DELTAFI_PATH}/deltafi-cli/deltafi

${DELTAFI_CLI} status > /dev/null || echo "No running DeltaFi detected."

echo "Loading the following docker images:"
cat airgap.repo.manifest
docker load -i airgap.repo.tar

${DELTAFI_CLI} install

echo "The following plugins will be installed:"
grep -ve "^#" "$PLUGIN_LIST_FILE" | sed 's|{{VERSION}}|'"$DELTAFI_VERSION"'|g'

while IFS= read -r line; do
    if [[ $line =~ ^# ]]; then
        continue
    fi
    # Token replacement
    line="${line//\{\{VERSION\}\}/$DELTAFI_VERSION}"

    image_tag=${line##*/}  #<-- Dark incantation.  Matches everything after the last '/'
    registry=${line%/"$image_tag"}
    if [[ "$registry" == "$line" ]]; then
        registry="docker.io"
    fi

    ${DELTAFI_CLI} install-plugin -i "$registry" "org.deltafi:$image_tag"

    PLUGIN_NAME=$(echo $line | sed 's|.*/\(.*\):.*|\1|g')
    while ! ${DELTAFI_CLI} list-plugins | grep "$PLUGIN_NAME" > /dev/null; do sleep 1; echo Waiting for ${PLUGIN_NAME} plugin; done

done < "$PLUGIN_LIST_FILE"

sleep 5

${DELTAFI_CLI} system-snapshot import "$SNAPSHOT_FILE"
SNAPSHOT_ID=$(${DELTAFI_CLI} system-snapshot list | grep -B2 air-gap | sed 's|.*\"\(.*\)\",|\1|g' | sed 1q)
${DELTAFI_CLI} system-snapshot restore "$SNAPSHOT_ID"

_confirm "Do you want to install 'deltafi' in /usr/local/bin?" && "${DELTAFI_PATH}/deltafi-cli/install.sh"

popd > /dev/null || exit
