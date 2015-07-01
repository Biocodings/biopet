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

package nl.lumc.sasc.biopet.extensions.kraken

import java.io.File

import nl.lumc.sasc.biopet.core.BiopetCommandLineFunction
import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }

/** Extension for Kraken */
class KrakenReport(val root: Configurable) extends BiopetCommandLineFunction {

  executable = config("exe", default = "kraken-report")
  override val versionRegex = """Kraken version (.*)""".r
  override val versionExitcode = List(0, 1)

  override val defaultCoreMemory = 4.0
  override val defaultThreads = 1

  override def versionCommand = new File(new File(executable).getParent, "kraken").getAbsolutePath + " --version"

  var db: File = config("db")
  var show_zeros: Boolean = config("show_zeros", default = false)

  @Input(doc = "Input raw kraken analysis")
  var input: File = _

  @Output(doc = "Output path kraken report")
  var output: File = _

  def cmdLine: String = {
    val cmd: String = "--db " + required(db) +
      conditional(show_zeros, "--show-zeros") +
      input.getAbsolutePath + ">" + output.getAbsolutePath
    cmd
  }
}