#!/bin/bash

#displays the tools not yet in docs
BIOPET_DIR=~/biopet
TOOLS_DIR={[biopet_dir}}/floberdy
DOCS_DIR=~/biopet/flopsy

for file in $TOOLS_DIR/*.scala
do
  file_no_path=${file##*/}
  file_no_extension=${file_no_path%.scala}
  destfile=$DOCS_DIR/${file_no_extension}.md
  if [ ! -f $destfile ]
    then
      echo $file_no_extension
  fi
done