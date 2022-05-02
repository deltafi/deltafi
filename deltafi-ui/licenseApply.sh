#!/bin/bash

function applyLicense {
  grep -q Copyright $2
  if [ $? -ne 0 ]; then
    cat $1 $2 > $2.new && mv $2.new $2
  fi
}

for EXT in vue html; do
  echo -e "<!--\n$(cat HEADER)\n-->\n" > HEADER.$EXT

  for FILE in $(find src -name "*.${EXT}"); do
    applyLicense HEADER.$EXT $FILE
  done

  rm HEADER.$EXT
done

for EXT in js ts scss; do
  echo -e "/*\n$(cat HEADER)\n*/\n" > HEADER.$EXT

  for FILE in $(find src -name "*.${EXT}"); do
    applyLicense HEADER.$EXT $FILE
  done

  rm HEADER.$EXT
done
