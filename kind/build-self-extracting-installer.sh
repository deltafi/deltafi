#!/usr/bin/env bash
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

# vim: filetype=bash
set -e

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

BASE_PATH="$( cd "$( dirname "$(_readlink -f "${BASH_SOURCE[0]}")")" &> /dev/null && pwd )"
export BASE_PATH
DELTAFI_PATH="$( cd "$BASE_PATH/.." &> /dev/null && pwd)"
TEMP_TREE=$BASE_PATH/deltafi

rm -rf "$TEMP_TREE"
mkdir -p "$TEMP_TREE"
LATEST_RELEASE=$(git describe --tags --abbrev=0)
echo "$LATEST_RELEASE" > "$TEMP_TREE/LATEST_RELEASE"
cp -rf "$DELTAFI_PATH/charts" "$TEMP_TREE"
cp -rf "$DELTAFI_PATH/deltafi-cli" "$TEMP_TREE"
cp -rf "$DELTAFI_PATH/kind" "$TEMP_TREE"
cp -rf "$DELTAFI_PATH/bootstrap.sh" "$TEMP_TREE"
cp -rf "$DELTAFI_PATH/LICENSE" "$TEMP_TREE"
rm -rf "$TEMP_TREE/kind/deltafi" "$TEMP_TREE/*/build" "$TEMP_TREE/kind/self-*.sh" "$TEMP_TREE/*/*/logs" "$TEMP_TREE/*/logs" "$TEMP_TREE/kind/data"

cd "$BASE_PATH"
tar -pczf archive.tar.gz deltafi

cat <<EOF > "$BASE_PATH/kind-install.sh"
#!/bin/sh
if [ -e deltafi ]; then
  echo "'deltafi' directory already exists here!"
  echo "You might try running deltafi/bootstrap.sh"
  exit 1
fi
cat <<ARCHIVE | base64 -d | tar -pzxf -
$(base64 < archive.tar.gz)
ARCHIVE
rm -f archive.tar.gz
cd deltafi
./bootstrap.sh
EOF

rm -rf "$BASE_PATH/archive.tar.gz" "$TEMP_TREE"
chmod +x "$BASE_PATH/kind-install.sh"
