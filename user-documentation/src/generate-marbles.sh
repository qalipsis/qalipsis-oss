#!/bin/sh

absolute_pwd="$(pwd)/$(dirname $0)"

find "$absolute_pwd" -type f -name *.mbl -print | while read mbl
do
  cd "$(dirname "$mbl")"
  marblesgen $(basename "$mbl")
done