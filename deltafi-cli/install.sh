#!/usr/bin/env bash
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

# Hack to allow non-sudo installs in docker containers
export SUDO=$(which sudo)

export DELTAFICLI_WORKDIR=$(cd $(dirname $(_readlink -f $0)) && pwd)
export DELTAFI=${DELTAFICLI_WORKDIR}/deltafi
[[ -L /usr/local/bin/deltafi ]] && echo "Replacing existing deltafi CLI installation $(ls -la /usr/local/bin/deltafi)"
${SUDO} ln -fs ${DELTAFI} /usr/local/bin || ln -fs ${DELTAFI} /usr/local/bin
if [[ ! -f ${DELTAFICLI_WORKDIR}/config ]]; then
    cp ${DELTAFICLI_WORKDIR}/config.template ${DELTAFICLI_WORKDIR}/config
    echo "Installing default config at ${DELTAFICLI_WORKDIR}/config"
fi
