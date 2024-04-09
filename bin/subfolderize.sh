#!/usr/bin/env bash

# move any old uuid folders into 3-hex character prefixed subfolders
# this is a one-time operation that should be run during the upgrade from a 0.102.x version to a newer version

DIR=$1
[ -z "$DIR" ] && echo "USAGE: subfolderize.sh PATH" && exit 1
[ ! -d "$DIR" ] && echo "Specify a valid path." && exit 1

for i in {0..4095}; do
  x=$(printf %03x $i)
  mkdir -p $DIR/$x

  mv $DIR/$x*-* $DIR/$x >/dev/null 2>&1
done

