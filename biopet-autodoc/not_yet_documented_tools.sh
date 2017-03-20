#!/bin/bash

#displays the tools not yet in docs
BIOPET_DIR=~/biopet
TOOLS_DIR=$BIOPET_DIR/biopet-tools/src/main/scala/nl/lumc/sasc/biopet/tools
DOCS_DIR=$BIOPET_DIR/docs/tools

echo "tools:" > config.yml
for file in $TOOLS_DIR/*.scala
do
  file_no_path=${file##*/}
  file_with_md=${file_no_path%.scala}.md
  destfile=$DOCS_DIR/$file_with_md
  if [ ! -f $destfile ]
    then
      echo "  $file_with_md" >> config.yml
  fi
done
