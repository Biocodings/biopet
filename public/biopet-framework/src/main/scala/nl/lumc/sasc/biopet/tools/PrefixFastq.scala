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
package nl.lumc.sasc.biopet.tools

import java.io.File

import htsjdk.samtools.fastq.{ AsyncFastqWriter, BasicFastqWriter, FastqReader, FastqRecord }
import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.core.{ ToolCommand, ToolCommandFuntion }
import org.broadinstitute.gatk.utils.commandline.{ Argument, Input, Output }

/**
 * Queue class for PrefixFastq tool
 *
 * Created by pjvan_thof on 1/13/15.
 */
class PrefixFastq(val root: Configurable) extends ToolCommandFuntion {
  javaMainClass = getClass.getName

  override val defaultCoreMemory = 1.0

  @Input(doc = "Input fastq", shortName = "I", required = true)
  var inputFastq: File = _

  @Output(doc = "Output fastq", shortName = "o", required = true)
  var outputFastq: File = _

  @Argument(doc = "Prefix seq", required = true)
  var prefixSeq: String = _

  /**
   * Creates command to execute extension
   * @return
   */
  override def commandLine = super.commandLine +
    required("-i", inputFastq) +
    required("-o", outputFastq) +
    optional("-s", prefixSeq)
}

object PrefixFastq extends ToolCommand {
  /**
   * Create a PrefixFastq class object with a sufix ".prefix.fastq" in the output folder
   *
   * @param root parent object
   * @param input input file
   * @param outputDir outputFolder
   * @return PrefixFastq class object
   */
  def apply(root: Configurable, input: File, outputDir: String): PrefixFastq = {
    val prefixFastq = new PrefixFastq(root)
    prefixFastq.inputFastq = input
    prefixFastq.outputFastq = new File(outputDir, input.getName + ".prefix.fastq")
    prefixFastq
  }

  /**
   * Args for commandline program
   * @param input input fastq file (can be zipper)
   * @param output output fastq file (can be zipper)
   * @param seq Seq to prefix the reads with
   */
  case class Args(input: File = null, output: File = null, seq: String = null) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('i', "input") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(input = x)
    }
    opt[File]('o', "output") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(output = x)
    }
    opt[String]('s', "seq") required () maxOccurs 1 valueName "<prefix seq>" action { (x, c) =>
      c.copy(seq = x)
    }
  }

  /**
   * Program will prefix reads with a given seq
   *
   * @param args the command line arguments
   */
  def main(args: Array[String]): Unit = {
    logger.info("Start")

    val argsParser = new OptParser
    val cmdArgs: Args = argsParser.parse(args, Args()) getOrElse sys.exit(1)

    val writer = new AsyncFastqWriter(new BasicFastqWriter(cmdArgs.output), 3000)
    val reader = new FastqReader(cmdArgs.input)

    var counter = 0
    while (reader.hasNext) {
      val read = reader.next()

      val maxQuality = read.getBaseQualityString.max

      val readHeader = read.getReadHeader
      val readSeq = cmdArgs.seq + read.getReadString
      val baseQualityHeader = read.getBaseQualityHeader
      val baseQuality = Array.fill(cmdArgs.seq.length)(maxQuality).mkString + read.getBaseQualityString

      writer.write(new FastqRecord(readHeader, readSeq, baseQualityHeader, baseQuality))

      counter += 1
      if (counter % 1e6 == 0) logger.info(counter + " reads processed")
    }

    if (counter % 1e6 != 0) logger.info(counter + " reads processed")
    writer.close()
    reader.close()
    logger.info("Done")
  }
}
