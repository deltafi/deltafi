#!/bin/bash

echo "python gradle wrapper called: $1"
if [ "$1" == "clean" ]; then
  rm -rf pyproject.toml deltafi_action_kit.egg-info/ dist
  exit 0

elif [ "$1" == "build" ] || [ "$1" == "build-default" ]; then

  if [ "$1" == "build-default" ]; then
    PYTHONEXE=python3
  else
    if [ -x /usr/bin/python3.7 ]; then
      PYTHONEXE=python3.7
    elif [ -x /usr/bin/python3.8 ]; then
      PYTHONEXE=python3.8
    elif [ -x /usr/bin/python3.9 ]; then
      PYTHONEXE=python3.9
    elif [ -x /usr/bin/python3.10 ]; then
      PYTHONEXE=python3.10
    elif [ -x /usr/bin/python3.11 ]; then
      PYTHONEXE=python3.11
    elif [ -x /usr/bin/python3 ]; then
      PYTHONEXE=python3
    else
      echo "Unable to find suitable python"
      exit 1
    fi
  fi

  PACKAGE_BUILD_VERSION=$(git describe --tags | cut -f1,2 -d'-' | tr '-' 'b')
  sed "s/XXX_VERSION_XXX/$PACKAGE_BUILD_VERSION/" pyproject.toml-template > pyproject.toml
  cp -p deltafi/plugin.py plugin.bk

  sed "s/XXX_VERSION_XXX/$PACKAGE_BUILD_VERSION/" plugin.bk > deltafi/plugin.py

  $PYTHONEXE -m build
  RESULT=$?

  rm -f deltafi/plugin.py
  mv plugin.bk deltafi/plugin.py

  if [ "$RESULT" == "0" ]; then
    exit 0
  else
    echo "If your build fails, you may need to instgall these:"
    echo "sudo $PYTHONEXE -m pip install --upgrade pip setuptools wheel build twine"

  fi
fi

exit 1
