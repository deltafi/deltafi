#!/bin/bash

set -u
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

if [[ -t 1 ]]; then
    tty_escape() { printf "\033[%sm" "$1"; }
else
    tty_escape() { :; }
fi

tty_bolden() { tty_escape "1;$1"; }
tty_blue="$(tty_bolden 34)"
tty_yellow="$(tty_bolden 33)"
tty_green="$(tty_bolden 32)"
tty_red="$(tty_bolden 31)"
tty_bold="$(tty_bolden 39)"
tty_reset="$(tty_escape 0)"

shell_join() {
  local arg
  printf "%s" "$1"
  shift
  for arg in "$@"
  do
    printf " "
    printf "%s" "${arg// /\ }"
  done
}

chomp() {
    printf "%s" "${1/"$'\n'"/}"
}

bullet() {
  printf "${tty_blue}==${tty_green}>${tty_bold} %s${tty_reset}\n" "$(shell_join "$@")"
}

warn() {
  printf "${tty_yellow}  ! ${tty_red}Warning${tty_reset}: %s\n" "$(chomp "$1")"
}

info() {
  printf "    %s\n" "$(shell_join "$@")"
}

abort() {
    printf "%s ${tty_red}...Halting${tty_reset}\n" "$@" >&2
    exit 1
}

getc() {
  local save_state
  save_state="$(/bin/stty -g)"
  /bin/stty raw -echo
  IFS='' read -r -n 1 -d '' "$@"
  /bin/stty "${save_state}"
}

wait_for_user() {
  if [[ -z "${NONINTERACTIVE-}" ]]; then
    local c
    echo
    echo "    Press ${tty_bold}RETURN${tty_reset}/${tty_bold}ENTER${tty_reset} to continue or any other key to abort:"
    getc c
    # we test for \r and \n because some stuff does \r instead
    if ! [[ "${c}" == $'\r' || "${c}" == $'\n' ]]
    then
      warn "Terminated..."
      exit 1
    fi
  fi
}

tool_exists() {
    command -v "$1" &> /dev/null
}

export SUDO=
if tool_exists sudo; then
  SUDO=sudo
fi

require_tools() {
    local MISSING_TOOL=false
    for tool in "$@"; do
        if ! tool_exists "$tool"; then
            warn "'${tool}' must be installed"
            MISSING_TOOL=true
        fi
    done
    [[ "$MISSING_TOOL" == "false" ]] || abort "Install missing tools and try again"
}

require_docker() {
  bullet "Verifying docker installation"
  info "Docker or Docker Desktop is a requirement for installing DeltaFi"
  require_tools docker
  info "Detected $(docker --version)"
}

install_homebrew() {
  if ! tool_exists brew; then
    bullet "Installing homebrew package manager"
    require_tools curl bash
    info "This will require downloading and executing an install script from github"
    info "See http://brew.sh for details or to install manually"
    wait_for_user
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  else
    bullet "Homebrew package manager already installed"
  fi
}

configure_install() {

  # ROOT_PATH is the base of the deltafi repo
  ROOT_PATH="$( cd "$( dirname "$(_readlink -f "${BASH_SOURCE[0]}:-PWD")")" &> /dev/null && pwd )"
  bullet "Configuring at path: $ROOT_PATH"
  if [[ -x "${ROOT_PATH}/bootstrap.sh" && -d "${ROOT_PATH}/deltafi-cli" && -d "${ROOT_PATH}/kind" && -d "${ROOT_PATH}/charts" ]]; then
    info "Minimum deltafi files detected at $ROOT_PATH"
  else
    abort "Unable to execute install of DeltaFi from $ROOT_PATH"
  fi
  export ROOT_PATH

  bullet "Resolve latest DeltaFi release"
  pushd "$ROOT_PATH" >/dev/null
  if [[ -f "$ROOT_PATH"/LATEST_RELEASE ]]; then
    LATEST_RELEASE=$(cat "$ROOT_PATH/LATEST_RELEASE")
  else
    LATEST_RELEASE=$(git describe --tags --abbrev=0)
  fi
  export LATEST_RELEASE
  info "Latest release is: $LATEST_RELEASE"
  popd >/dev/null

  bullet "Installing DeltaFi CLI"
  info "You may be prompted for your administrator password..."
  "${ROOT_PATH}/kind/install.sh"
  require_tools deltafi cluster

  bullet "Adding GitLab Gradle configuration"
  if [[ ! -f $HOME/.gradle/gradle.properties ]]; then
    mkdir -p "$HOME/.gradle"
    touch "$HOME/.gradle/gradle.properties"
  fi
  grep "gitLabTokenType=" "$HOME/.gradle/gradle.properties" || echo "gitLabTokenType=Private-Token" >> "$HOME/.gradle/gradle.properties"
  grep "gitLabToken=" "$HOME/.gradle/gradle.properties" || echo "gitLabToken=PutYourTokenHere" >> "$HOME/.gradle/gradle.properties"
}

install_brew_packages() {
  require_tools brew xcode-select
  while ! xcode-select -p >/dev/null; do
    bullet "Installing XCode command line tools"
    info "A dialog will appear to install command line tools.  When complete, press enter"
    xcode-select --install
    wait_for_user
  done
  bullet "Installing required packages via Homebrew"
  info "This process may take several minutes.  Packages will be downloaded from the internet"
  wait_for_user
  [[ -f $ROOT_PATH/kind/Brewfile ]] || abort "Unable to locate Brewfile at ${ROOT_PATH}/kind/Brewfile"
  brew bundle install --file="$ROOT_PATH/kind/Brewfile"
}

install_docker() {

  if ! tool_exists docker; then
    info "Installing docker using https://get.docker.io"
    info "After the docker install, you will need to log out and back in, then rerun the bootstrap"
    info "If the docker install fails, you will need to install docker yourself"
    wait_for_user

    if tool_exists yum; then
      ${SUDO} yum remove runc
      ${SUDO} yum install -y curl
    elif tool_exists apt; then
      ${SUDO} apt-get install -y curl
    fi

    curl -fsSL https://get.docker.com -o get-docker.sh

    ${SUDO} sh "get-docker.sh" || warn "Docker installed, but possible issues"
    rm -f get-docker.sh
    info "Docker installed, now configuring"
    ${SUDO} usermod -aG docker "$USER" || warn "There may be an issue with the docker group"
    ${SUDO} systemctl start docker
    ${SUDO} systemctl enable docker.service
    ${SUDO} systemctl enable containerd.service
    abort "You will need to run the bootstrap script again now that docker is installed"
  else
    docker run hello-world || abort "Your docker install is incomplete.  You may need to log out and run the bootstrap script again."
  fi
}

install_linux_packages() {
  bullet "Installing required packages"
  if tool_exists yum; then
    info "Checking for necessary packages via yum"
    ${SUDO} yum install -y git curl wget java-11-openjdk java-11-openjdk-devel python3 python3-pip
    if ! tool_exists kubectl; then
      cat <<EOF | ${SUDO} tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-\$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF
      ${SUDO} yum install -y kubectl
    fi
    ${SUDO} pip3 install --upgrade -q poetry
  elif tool_exists apt; then
    info "Checking for necessary packages via apt/snap"
    wait_for_user
    ${SUDO} apt-get install -y ca-certificates curl
    ${SUDO} apt-get update
    ${SUDO} apt-get install -y uidmap dbus-user-session fuse-overlayfs slirp4netns git wget snapd vim tig tmux openjdk-11-jdk python3 python3-pip
    if ! tool_exists kubectl; then
      ${SUDO} snap install --classic kubectl
      mkdir -p ~/.kube/kubens
    fi
    if ! tool_exists helm; then
      ${SUDO} snap install --classic helm
    fi
    if ! tool_exists kubectx; then
      ${SUDO} snap install kubectx
    fi
    if ! tool_exists yq; then
      ${SUDO} snap install yq
    fi
    ${SUDO} pip3 install --upgrade -q poetry
    ${SUDO} ln -s /snap/bin/* /usr/local/bin || warn "All tools may not be configured correctly"
  elif tool_exists apk; then
    info "Checking for necessary packages via apk"
    wait_for_user
    ${SUDO} apk add ncurses curl git wget vim tig tmux python3 py3-pip openjdk11-jdk kubectx helm yq
    ${SUDO} apk add kubectl --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing/
    ${SUDO} pip3 install --upgrade -q poetry
  fi

  if ! tool_exists kubens; then
    info "Installing kubectx... installing from a git repo"
    wait_for_user
    ${SUDO} git clone https://github.com/ahmetb/kubectx /opt/kubectx
    ${SUDO} ln -s /opt/kubectx/kubens /usr/local/bin/kubens
  fi
  if ! tool_exists helm; then
    info "Installing helm... running a script from the helm repo as ${SUDO}"
    wait_for_user
    ${SUDO} bash -c "PATH=$PATH:/usr/local/bin $(curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3)"
  fi
  if ! tool_exists yq; then
    info "Installing yq... installing from a git repo"
    wait_for_user
    if is_arm; then
      ${SUDO} wget -q https://github.com/mikefarah/yq/releases/download/v4.27.5/yq_linux_arm64 -O /usr/bin/yq && ${SUDO} chmod +x /usr/bin/yq
    else
      ${SUDO} wget -q https://github.com/mikefarah/yq/releases/download/v4.27.5/yq_linux_amd64 -O /usr/bin/yq && ${SUDO} chmod +x /usr/bin/yq
    fi
  fi
  if ! tool_exists kind; then
    info "Installing KinD... installing from the KinD website"
    wait_for_user
    if is_arm; then
      ${SUDO} wget -q https://kind.sigs.k8s.io/dl/v0.15.0/kind-linux-arm64 -O /usr/local/bin/kind && ${SUDO} chmod +x /usr/local/bin/kind
    else
      ${SUDO} wget -q https://kind.sigs.k8s.io/dl/v0.15.0/kind-linux-amd64 -O /usr/local/bin/kind && ${SUDO} chmod +x /usr/local/bin/kind
    fi
  fi
  require_tools git docker kubectl kubens helm curl wget kind yq
}

install_cluster() {
  require_tools cluster deltafi
  bullet "Installing a DeltaFi KinD cluster"
  deltafi loc build install
  deltafi set-admin-password password
  deltafi versions
}

is_x86() {
  ARCH="$(uname -m)"
  if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
    return 0
  else
    return 1
  fi
}

is_arm() {
  ARCH="$(uname -m)"
  if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
    return 0
  else
    return 1
  fi
}

# Now the install...

OS="$(uname)"
ARCH="$(uname -m)"
if [[ "${OS}" == "Linux" ]]; then
  DELTAFI_ON_LINUX=1
  bullet "Executing Linux Deltafi install"
elif [[ "${OS}" == "Darwin" ]]; then
  DELTAFI_ON_MACOS=1
  bullet "Executing MacOS DeltaFi install"
else
  abort "DeltaFi installer is only supported on macOS and Linux."
fi

# MacOS install
if [[ -n "${DELTAFI_ON_MACOS-}" ]]; then

  require_docker
  install_homebrew
  configure_install
  install_brew_packages
  install_cluster

  bullet "Installation is complete"
  open https://local.deltafi.org
  info "You should have the DeltaFi UI open in a local browser window"
  info "If you have 'Your connection is not private' on the Chrome browser, type 'thisisunsafe'"
  info "If you have certificate warnings in other browsers, you may have to work through security dialogs"
  info "    username: admin"
  info "    password: password"
  echo
  info "Now you can enable flows, upload content to the passthrough flow, etc."
fi

# Linux install (Debian, CentOS)
if [[ -n "${DELTAFI_ON_LINUX-}" ]]; then
  require_docker
  install_linux_packages
  configure_install
  install_cluster
fi

