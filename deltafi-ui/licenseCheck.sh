#!/bin/bash

RETURN=0

function check {
  DIFF=$(comm -23 $1 $2 2> /dev/null)
  if [ "${DIFF}" != "" ]; then
    RETURN=1
    echo -e "Error(s) detected in $FILE:\n$DIFF"
  fi
}

for EXT in vue html; do
  echo -e "<!--\n$(cat HEADER)\n-->\n" > HEADER.$EXT

  for FILE in $(find src -name "*.${EXT}"); do
    check HEADER.$EXT $FILE
  done

  rm HEADER.$EXT
done

for EXT in js ts scss; do
  echo -e "/*\n$(cat HEADER)\n*/\n" > HEADER.$EXT

  for FILE in $(find src -name "*.${EXT}"); do
    check HEADER.$EXT $FILE
  done

  rm HEADER.$EXT
done

exit $RETURN