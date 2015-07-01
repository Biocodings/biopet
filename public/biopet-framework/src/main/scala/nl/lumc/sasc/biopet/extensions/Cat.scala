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
package nl.lumc.sasc.biopet.extensions

import java.io.File

import nl.lumc.sasc.biopet.core.BiopetCommandLineFunction
import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }

/**
 * Extension for GNU cat
 */
class Cat(val root: Configurable) extends BiopetCommandLineFunction {
  @Input(doc = "Input file", required = true)
  var input: List[File] = Nil

  @Output(doc = "Unzipped file", required = true)
  var output: File = _

  executable = config("exe", default = "cat")

  /** return commandline to execute */
  def cmdLine = required(executable) + repeat(input) + " > " + required(output)
}

/**
 * Object for constructors for cat
 */
object Cat {
  /**
   * Basis constructor
   * @param root root object for config
   * @param input list of files to use
   * @param output output File
   * @return
   */
  def apply(root: Configurable, input: List[File], output: File): Cat = {
    val cat = new Cat(root)
    cat.input = input
    cat.output = output
    return cat
  }
}