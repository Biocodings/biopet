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

class SamtoolsMpileup(val root: Configurable) extends Samtools {
  @Input(doc = "Bam File")
  var input: File = _

  @Output(doc = "output File")
  var output: File = _

  @Input(doc = "Reference fasta")
  var reference: File = config("reference")

  @Input(doc = "Interval bed")
  var intervalBed: File = config("interval_bed")

  var disableBaq: Boolean = config("disable_baq")
  var minMapQuality: Option[Int] = config("min_map_quality")
  var minBaseQuality: Option[Int] = config("min_base_quality")

  def cmdBase = required(executable) +
    required("mpileup") +
    optional("-f", reference) +
    optional("-l", intervalBed) +
    optional("-q", minMapQuality) +
    optional("-Q", minBaseQuality) +
    conditional(disableBaq, "-B")
  def cmdPipeInput = cmdBase + "-"
  def cmdPipe = cmdBase + required(input)
  def cmdLine = cmdPipe + " > " + required(output)
}

object SamtoolsMpileup {
  def apply(root: Configurable, input: File, output: File): SamtoolsMpileup = {
    val mpileup = new SamtoolsMpileup(root)
    mpileup.input = input
    mpileup.output = output
    return mpileup
  }

  def apply(root: Configurable, input: File, outputDir: String): SamtoolsMpileup = {
    val dir = if (outputDir.endsWith("/")) outputDir else outputDir + "/"
    val outputFile = new File(dir + swapExtension(input.getName))
    return apply(root, input, outputFile)
  }

  def apply(root: Configurable, input: File): SamtoolsMpileup = {
    return apply(root, input, new File(swapExtension(input.getAbsolutePath)))
  }

  private def swapExtension(inputFile: String) = inputFile.stripSuffix(".bam") + ".mpileup"
}