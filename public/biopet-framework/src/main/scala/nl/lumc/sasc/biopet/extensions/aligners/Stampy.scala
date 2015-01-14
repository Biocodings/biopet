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
package nl.lumc.sasc.biopet.extensions.aligners

import nl.lumc.sasc.biopet.core.BiopetCommandLineFunction
import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }
import java.io.File

class Stampy(val root: Configurable) extends BiopetCommandLineFunction {
  @Input(doc = "FastQ file R1", shortName = "R1")
  var R1: File = _

  @Input(doc = "FastQ file R2", shortName = "R2", required = false)
  var R2: File = _

  @Input(doc = "The reference file for the bam files.", shortName = "ref")
  var reference: File = config("reference", required = true)

  @Input(doc = "The genome prefix.")
  var genome: File = config("genome", required = true)

  @Input(doc = "The hash prefix")
  var hash: File = config("hash", required = true)

  @Output(doc = "Output file SAM", shortName = "output")
  var output: File = _

  // options set via API or config
  //  var numrecords: String = config("numrecords", default = "all")
  var solexa: Boolean = config("solexa", default = false)
  var solexaold: Boolean = config("solexaold", default = false)
  var sanger: Boolean = config("sanger", default = false)

  var insertsize: Option[Int] = config("insertsize", default = 250)
  var insertsd: Option[Int] = config("insertsd", default = 60)
  var insertsize2: Option[Int] = config("insertsize2", default = -2000)
  var insertsd2: Option[Int] = config("insertsd2", default = -1)

  var sensitive: Boolean = config("sensitive", default = false)
  var fast: Boolean = config("fast", default = false)

  var readgroup: String = config("readgroup")
  var verbosity: Option[Int] = config("verbosity", default = 2)
  var logfile: String = config("logfile")

  executable = config("exe", default = "stampy.py", freeVar = false)
  override val versionRegex = """stampy v(.*) \(.*\), .*""".r
  override val versionExitcode = List(0, 1)

  /// Stampy uses approx factor 1.1 times the size of the genome in memory.
  override val defaultVmem = "4G"
  override val defaultThreads = 8

  override def versionCommand = executable + " --help"

  def cmdLine: String = {
    var cmd: String = required(executable) +
      optional("-t", nCoresRequest) +
      conditional(solexa, "--solexa") +
      conditional(solexaold, "--solexaold") +
      conditional(sanger, "--sanger") +
      optional("--insertsize", insertsize) +
      optional("--insertsd", insertsd)

    // Optionally start Mate Pair alignment, if set, the aligner will
    // assign MP reads as MP, otherwise in PE mode, these reads will
    // be aligned with the bits RR or FF showing a False Inversion event
    if (insertsd2.getOrElse(-1) != -1) {
      cmd += optional("--insertsize2", insertsize2) +
        optional("--insertsd2", insertsd2)
    }

    cmd += conditional(sensitive, "--sensitive") +
      conditional(fast, "--fast") +
      optional("--readgroup", readgroup) +
      optional("-v", verbosity) +
      optional("--logfile", logfile) +
      " -g " + required(genome) +
      " -h " + required(hash) +
      " -o " + required(output) +
      " -M " + required(R1) + optional(R2)
    return cmd
  }
}