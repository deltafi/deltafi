#!/usr/bin/env bash
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

# vim: filetype=bash
set -e

abort() {
    printf "%s\n" "$@" >&2
    exit 1
}

if [ -z "${BASH_VERSION:-}" ]; then
    abort "Bash is the required interpreter for this script.  Please install the latest Bash for your OS."
fi

[[ -n "${POSIXLY_CORRECT+1}" ]] && abort "You cannot run this install in POSIXLY_CORRECT mode.  Unset POSIXLY_CORRECT and retry"

if [[ -n "${INTERACTIVE-}" && -n "${NONINTERACTIVE-}" ]]; then
  abort 'Both `$INTERACTIVE` and `$NONINTERACTIVE` are set. Please unset at least one variable and try again.'
fi

export EDITOR=${EDITOR:-vim}

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

getc() {
  local save_state
  save_state="$(/bin/stty -g)"
  /bin/stty raw -echo
  IFS='' read -r -n 1 -d '' "$@"
  /bin/stty "${save_state}"
}

normal=$(tput sgr0)
bold=$(tput bold)
red=$(tput setaf 1)
green=$(tput setaf 2)
yellow=$(tput setaf 3)
blue=$(tput setaf 33)
magenta=$(tput setaf 5)
cyan=$(tput setaf 6)
white=$(tput setaf 7)
black=$(tput setaf 0)
gray=$(tput setaf 8)
orange=$(tput setaf 208)

_wait_for_user() {
  prompt=${1:-"Press any key to continue"}
  if [[ -z "${NONINTERACTIVE-}" ]]; then
    local c
    echo
    echo "       ${cyan}▶ ${green}$prompt${normal}"
    echo
    getc c
  fi
}

_log() {
  local timestamp
  timestamp=${gray}$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  echo "${yellow}>  $timestamp${normal} $1"
}

_attention() {
  {
    printf "\n%s\n\n" "${gray}       ▶ ${blue}${*}${normal}"
  } 1>&2
}

_info() {
  {
    local prepend="${orange} ‣ ${normal}"
    case $1 in
    -w)
      prepend="${red} ‼ ${normal}"
      shift
      ;;
    -h)
      prepend="${yellow} ! ${normal}"
      shift
      ;;
    -s)
      echo
      prepend="${blue} ╭ ${normal}"
      shift
      ;;
    -e)
      prepend="${blue} ╰ ${normal}"
      shift
      ;;
    -q)
      prepend="   "
      shift
      ;;
    -a)
      prepend="${blue} │${gray} "
      shift
      ;;
    -c)
      prepend="${blue} │ ${green}✓ ${gray} "
      shift
      ;;
    esac
    printf "      %s\n" "${prepend}${*}${normal}"
  } 1>&2
}

_warn() {
  _info -w "$@"
}

_message() {
  color="$1"
  msg="$2"
  shift 2
  printf "[%s%-4s%s]   %s\n" "$color" "$msg" "${normal}" "$@"
}

_ok_annotated() {
  printf "[%s%-4s%s] ${blue}╰${normal} %s\n" "$green" " OK" "${normal}" "$@"
}

_ok() {
  _message "${green}" " OK " "$@"
}

_down() {
  _message "${yellow}" "DOWN" "$@"
}

_fail() {
  _message "${red}" "FAIL" "$@"
}

_conditional() {
  title=$1
  shift

  if "${@}"; then
    _ok_annotated $title
  else
    _fail $title
    return 1
  fi
}

_annotated_subshell() {

  local filters=()
  while true; do
    case $1 in
    -h)
      _info -s "$2"
      shift 2
      ;;
    -v)
      filters+=("$2")
      shift 2
      ;;
    *)
      break
      ;;
    esac
  done
  {
    "${@}" 2>&1 | while read -r line; do
      local remove=
      for filter in "${filters[@]}"; do
        if [[ $line =~ $filter ]]; then
          remove=true
          continue
        fi
      done
      [[ -n "${remove}" ]] && continue
      _info -a "$line"
    done
  } 2>&1
}

_confirm() {
  local prompt=${1:-"Are you sure?"}
  read -r -p "         ${yellow}$prompt${normal} [y/N] " response
  case "$response" in
    [yY][eE][sS]|[yY])
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

# +++ VARIABLES

BASE_PATH="$( cd "$( dirname "$(_readlink -f "${BASH_SOURCE[0]}")")" &> /dev/null && pwd )"
export BASE_PATH
COMPOSE_PATH="$( cd "$BASE_PATH/.." &> /dev/null && pwd)"
DELTAFI_PATH="$( cd "$COMPOSE_PATH/.." &> /dev/null && pwd)"
AIRGAP_DISTRO_TREE=$BASE_PATH/deltafi

DELTAFI_VERSION=$(cat "${DELTAFI_PATH}/deltafi-cli/VERSION")

VALUES_FILE="${BASE_PATH}/airgap.values.yaml"
STATIC_REPO_FILTER_FILE="${BASE_PATH}/airgap.static-repo-filter.manifest"
STATIC_REPO_LIST_FILE="${BASE_PATH}/airgap.static-repo-list.manifest"
REPO_FILTER_FILE="${BASE_PATH}/airgap.repo-filter.manifest"
REPO_LIST_FILE="${AIRGAP_DISTRO_TREE}/airgap.repo.manifest"
PLUGIN_LIST_FILE="${BASE_PATH}/airgap.plugin.manifest"
SNAPSHOT_FILE="${BASE_PATH}/airgap.snapshot.json"
AIRGAP_DISTRO_ARCHIVE=$BASE_PATH/airgap.archive.${DELTAFI_VERSION}.$(uname -m).tar.gz
AIRGAP_REPO_ARCHIVE="${AIRGAP_DISTRO_TREE}/airgap.repo.tar"
AIRGAP_INSTALL_TEMPLATE="${BASE_PATH}/airgap.install.sh"
AIRGAP_DISTRO_INSTALL=$BASE_PATH/airgap.install.${DELTAFI_VERSION}.sh

cd "$BASE_PATH"

# +++ HELPER FUNCTIONS

function capture_starting_docker_repo() {

  pushd "$BASE_PATH" > /dev/null
  cat <<EOF > "$REPO_FILTER_FILE"
# vim: set ft=conf:
#
# -----  Docker Image FILTER List
#
# This is pre-populated with all images currently in the local docker repo.
# *** ALL IMAGES listed here will be filtered out of the final distro.
# Remove any images from this list if you want them to be included in the final distro.
EOF

  _info -s "The following docker repos will be filtered out of the air-gapped repository"
  _info -a ""
  _annotated_subshell grep -ve "^#" "$STATIC_REPO_FILTER_FILE"
  _info -e ""

  if [[ -z "${NONINTERACTIVE-}" ]]; then
    _info "You will now be allowed to edit an additional list based on the images currently in the local repository."
    _info "Note: All images in the local repository that are not filtered will be included in the final installer."
    _wait_for_user
    docker images | sed 1d | awk '{ print $1 ":" $2 }' >> "$REPO_FILTER_FILE"
    $EDITOR "$REPO_FILTER_FILE"
  fi
}

function capture_airgap_repo_manifest() {
  docker images | sed 1d | awk '{ print $1 ":" $2 }' | grep -vFf "$REPO_FILTER_FILE" | grep -vFf "$STATIC_REPO_FILTER_FILE" > "$REPO_LIST_FILE"
  grep -ve "^#" < "$STATIC_REPO_LIST_FILE" >> "$REPO_LIST_FILE"
  repos=$(sort < "$REPO_LIST_FILE" | uniq)
  echo "$repos" > "$REPO_LIST_FILE"
}

function curate_install_files() {
  mkdir -p "$AIRGAP_DISTRO_TREE/bin"
  cat <<EOF > "$AIRGAP_DISTRO_TREE/bin/lazydocker"
#!/bin/sh
docker run -it --rm -v /var/run/docker.sock:/var/run/docker.sock -v \$HOME/.config/lazydocker:/.config/jesseduffield/lazydocker deltafi/lazydocker:v0.23.3-0 \$@
EOF

  cat <<EOF > "$AIRGAP_DISTRO_TREE/bin/yq"
#!/bin/sh
docker run --rm -i \\
  -v "\$PWD:\$PWD" \\
  -w "\$PWD" \\
  mikefarah/yq:4.44.3 \\
  "\$@"
EOF

  cat <<EOF > "$AIRGAP_DISTRO_TREE/bin/jq"
#!/bin/sh
docker run --rm -i \\
  -v "\$PWD:\$PWD" \\
  -w "\$PWD" \\
  ghcr.io/jqlang/jq:1.7.1 \\
  "\$@"
EOF

  chmod a+x "$AIRGAP_DISTRO_TREE"/bin/*
}

function create_distro() {
  _info -s "Creating air-gapped distro"
  pushd "$BASE_PATH" > /dev/null
  echo "$DELTAFI_VERSION" > "${AIRGAP_DISTRO_TREE}/VERSION"
  cp -rLf "$DELTAFI_PATH/migrations" "$AIRGAP_DISTRO_TREE"
  cp -rLf "$DELTAFI_PATH/deltafi-cli" "$AIRGAP_DISTRO_TREE"
  rm -rf "$AIRGAP_DISTRO_TREE/deltafi-cli/logs"
  rm -rf "$AIRGAP_DISTRO_TREE/deltafi-cli/build"
  rm -f "$AIRGAP_DISTRO_TREE/deltafi-cli/build.gradle"
  rm -rf "$AIRGAP_DISTRO_TREE/deltafi-cli/lib"
  rm -rf "$AIRGAP_DISTRO_TREE/deltafi-cli/commands/performance-test"
  cp -f "$COMPOSE_PATH/compose" "$AIRGAP_DISTRO_TREE/compose"
  cp -rLf "$COMPOSE_PATH/settings" "$AIRGAP_DISTRO_TREE/compose"
  rm -f "$AIRGAP_DISTRO_TREE/compose/settings/secrets/*.env"
  rm -f "$AIRGAP_DISTRO_TREE/compose/settings/env/*.env"
  rm -f "$AIRGAP_DISTRO_TREE/compose/settings/env/*.yaml"
  cp -f "$COMPOSE_PATH/docker-compose.yml" "$AIRGAP_DISTRO_TREE/compose"
  cp -f "$BASE_PATH/airgap.delete-policy.json" "$AIRGAP_DISTRO_TREE"
  cp -f "$AIRGAP_INSTALL_TEMPLATE" "$AIRGAP_DISTRO_TREE/install.sh"
  cp -f "$DELTAFI_PATH/LICENSE" "$AIRGAP_DISTRO_TREE"
  rm -rf "$AIRGAP_DISTRO_TREE/*/*/logs" "$AIRGAP_DISTRO_TREE/*/logs" "$AIRGAP_DISTRO_TREE/compose/data"
  cp -f "$PLUGIN_LIST_FILE" "$AIRGAP_DISTRO_TREE"
  mv "$SNAPSHOT_FILE" "$AIRGAP_DISTRO_TREE"
  cp -f "$VALUES_FILE" "$AIRGAP_DISTRO_TREE/values.yaml"
  _annotated_subshell tree -CA "$AIRGAP_DISTRO_TREE"
  _ok_annotated "Air-gapped distro created"

  popd > /dev/null
}

function install_plugins() {
  _attention "Installing DeltaFi plugins"

  _info -s "The following plugins will be installed:"
  _info -a ""
  _annotated_subshell grep -ve "^#" "$PLUGIN_LIST_FILE" | sed 's|{{VERSION}}|'"$DELTAFI_VERSION"'|g'
  _info -e ""

  if [[ -z "${NONINTERACTIVE-}" ]]; then
    _confirm "Do you wish to modify this list?" && $EDITOR "$PLUGIN_LIST_FILE"
  fi

  while IFS= read -r line; do
    if [[ $line =~ ^# ]]; then
      continue
    fi

    # Token replacement
    line="${line//\{\{VERSION\}\}/$DELTAFI_VERSION}"

    # Dark incantation.  Matches everything after the last '/'
    image_tag=${line##*/}
    registry=${line%/"$image_tag"}
    if [[ "$registry" == "$line" ]]; then
      registry="docker.io"
    fi

    _info -s "Installing plugin: $line"
    _annotated_subshell deltafi install-plugin -i "$registry" "org.deltafi:$image_tag"
    while ! deltafi list-plugins | grep "$image_tag" > /dev/null; do
      sleep 1
      _info -a "Waiting for ${image_tag} plugin"
    done

    _ok_annotated "Installed plugin: $line"
  done < "$PLUGIN_LIST_FILE"

  _info -s ""
  _annotated_subshell deltafi list-plugins
  _ok_annotated "All plugins installed"
}

function archive_distro() {
  _info -s "Archiving the air-gap distro"
  pushd "$BASE_PATH" > /dev/null
  INSTALL_SYMLINK="install-${DELTAFI_VERSION}.sh"
  ln -s deltafi/install.sh $INSTALL_SYMLINK
  _annotated_subshell tar -pczf "$AIRGAP_DISTRO_ARCHIVE" deltafi $INSTALL_SYMLINK > /dev/null
  _annotated_subshell ls -la "${AIRGAP_DISTRO_ARCHIVE}"
  rm -f $INSTALL_SYMLINK
  _ok_annotated "Distro archive created"
  popd > /dev/null
}

function freeze_dry_repo() {
  _info -s "Carbon freeze docker repo"
  _info -a "Pulling required images:"
  for repo in $(cat $REPO_LIST_FILE); do
    _annotated_subshell docker pull "$repo"
    _info -c "$repo"
  done
  _info -a "Freezing the following images:"
  _annotated_subshell cat "$REPO_LIST_FILE"
  _annotated_subshell docker save $(cat "$REPO_LIST_FILE") -o "$AIRGAP_REPO_ARCHIVE"
  _info -c "All images frozen"
  _ok_annotated "Docker repo ready for transport"
}

function clean() {
  rm -f "$BASE_PATH/archive.tar.gz"
  rm -rf "$AIRGAP_DISTRO_TREE" || sudo rm -rf "$AIRGAP_DISTRO_TREE"
  rm -f ./*90m20*
  rm -f airgap.repo-filter.manifest
  _ok "Cleanup completed"
}

function initial_setup() {
  rm -rf "$AIRGAP_DISTRO_TREE"
  rm -rf "$AIRGAP_DISTRO_ARCHIVE"
  rm -rf "$AIRGAP_DISTRO_INSTALL"
  mkdir -p "$AIRGAP_DISTRO_TREE/compose"

  _attention "Air-gapped DeltaFi Distribution Generator (AirGapInator)"
  _info "Version: ${DELTAFI_VERSION}"
  _info "Architecture: $(uname -m)"

  _info -s "Commencing pre-flight checks"
  if deltafi status > /dev/null; then
    _fail "DeltaFi is currently running.  Please 'deltafi uninstall' before running this script"
    exit 1
  else
    _info -c "No DeltaFi is running"
  fi

  if [[ -d $COMPOSE_PATH/data ]]; then
    _info -w "The compose data directory exists.  Prior configuration could affect the operation of this script."
    if _confirm "Would you like to delete the data directory?"; then
      rm -rf "$COMPOSE_PATH/data" || sudo rm -rf "$COMPOSE_PATH/data" || echo "Unable to remove $COMPOSE_PATH/data..."
    fi
  fi

  if [[ -d $COMPOSE_PATH/data ]]; then
    _info -h "The compose data directory still exists.  This may produce unexpected results."
  else
    _info -c "No compose data directory exists"
  fi

  _ok_annotated "Ready to begin"
}

# +++ BUILD SCRIPT

initial_setup

capture_starting_docker_repo

_attention "DeltaFi will now be installed locally"

_info -s "Installing DeltaFi"
_info -a ""
deltafi install -f "${VALUES_FILE}" || _fail "Failed to install DeltaFi"
_info -a ""
_ok_annotated "DeltaFi installed"

_info -s "Checking DeltaFi status"
_annotated_subshell deltafi status
_ok_annotated "Healthy DeltaFi system"

install_plugins

_info -s "Running config script"
pushd ${BASE_PATH} > /dev/null
sleep 2 # Wait for plugins to settle
_annotated_subshell "$BASE_PATH/airgap.config.sh"
popd > /dev/null
_ok_annotated "Config script complete"

echo
_info "Prior to the snapshot, you can now perform any manual configuration to the system."
_wait_for_user "Press any key when configuration is complete and snapshot is ready to be captured"

_info -s "Capture snapshot"
deltafi system-snapshot create "Configuration for air-gap deployment" > "$SNAPSHOT_FILE"
SNAPSHOT_ID=$(deltafi system-snapshot list | grep -B2 air-gap | sed 's|.*\"\(.*\)\",|\1|g' | sed 1q)
_info -c "Snapshot ID: $SNAPSHOT_ID"
_ok_annotated "Snapshot captured"

capture_airgap_repo_manifest
curate_install_files
freeze_dry_repo
create_distro
archive_distro
clean

_info "Install archive is located at $AIRGAP_DISTRO_ARCHIVE"

