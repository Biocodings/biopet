/**
 * Biopet is built on top of GATK Queue for building bioinformatic
 * pipelines. It is mainly intended to support LUMC SHARK cluster which is running
 * SGE. But other types of HPC that are supported by GATK Queue (such as PBS)
 * should also be able to execute Biopet tools and pipelines.
 *
 * Copyright 2014 Sequencing Analysis Support Core - Leiden University Medical Center
 *
 * Contact us at: sasc@lumc.nl
 *
 * A dual licensing mode is applied. The source code within this project that are
 * not part of GATK Queue is freely available for non-commercial use under an AGPL
 * license; For commercial users or users who do not want to follow the AGPL
 * license, please contact us to obtain a separate license.
 */
package nl.lumc.sasc.biopet.extensions.samtools

import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }
import java.io.File

class SamtoolsFlagstat(val root: Configurable) extends Samtools {
  @Input(doc = "Bam File")
  var input: File = _

  @Output(doc = "output File")
  var output: File = _

  def cmdLine = required(executable) + required("flagstat") + required(input) + " > " + required(output)
}

object SamtoolsFlagstat {
  def apply(root: Configurable, input: File, outputDir: File): SamtoolsFlagstat = {
    val flagstat = new SamtoolsFlagstat(root)
    flagstat.input = input
    flagstat.output = new File(outputDir, input.getName.stripSuffix(".bam") + ".flagstat")
    flagstat
  }
}