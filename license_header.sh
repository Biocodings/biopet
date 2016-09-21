#!/bin/bash
#
# Biopet is built on top of GATK Queue for building bioinformatic
# pipelines. It is mainly intended to support LUMC SHARK cluster which is running
# SGE. But other types of HPC that are supported by GATK Queue (such as PBS)
# should also be able to execute Biopet tools and pipelines.
#
# Copyright 2014 Sequencing Analysis Support Core - Leiden University Medical Center
#
# Contact us at: sasc@lumc.nl
#
# A dual licensing mode is applied. The source code within this project is freely available for non-commercial use under an AGPL
# license; For commercial users or users who do not want to follow the AGPL
# license, please contact us to obtain a separate license.
#

DIR=`readlink -f \`dirname $0\``
MODE=check

if [ $1 ] ; then
    MODE=$1
fi

mvn -f $DIR/pom.xml license:$MODE -Dlicense.header=$DIR/LICENSE