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

import java.io.{ PrintWriter, File }

import nl.lumc.sasc.biopet.utils.ConfigUtils._
import scala.collection.mutable

import scala.io.Source

/**
 * This tool can convert a tsv to a json file
 */
object SamplesTsvToJson extends ToolCommand {
  case class Args(inputFiles: List[File] = Nil, outputFile: Option[File] = None) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('i', "inputFiles") required () unbounded () valueName "<file>" action { (x, c) =>
      c.copy(inputFiles = x :: c.inputFiles)
    } text "Input must be a tsv file, first line is seen as header and must at least have a 'sample' column, 'library' column is optional, multiple files allowed"
    opt[File]('o', "outputFile") unbounded () valueName "<file>" action { (x, c) =>
      c.copy(outputFile = Some(x))
    }
  }

  /** Executes SamplesTsvToJson */
  def main(args: Array[String]): Unit = {
    val argsParser = new OptParser
    val commandArgs: Args = argsParser.parse(args, Args()) getOrElse sys.exit(1)

    val jsonString = stringFromInputs(commandArgs.inputFiles)
    commandArgs.outputFile match {
      case Some(file) => {
        val writer = new PrintWriter(file)
        writer.println(jsonString)
        writer.close()
      }
      case _ => println(jsonString)
    }
  }

  def mapFromFile(inputFile: File): Map[String, Any] = {
    val reader = Source.fromFile(inputFile)
    val lines = reader.getLines().toList.filter(!_.isEmpty)
    val header = lines.head.split("\t")
    val sampleColumn = header.indexOf("sample")
    val libraryColumn = header.indexOf("library")
    if (sampleColumn == -1) throw new IllegalStateException("Sample column does not exist in: " + inputFile)

    val sampleLibCache: mutable.Set[(String, Option[String])] = mutable.Set()

    val librariesValues: List[Map[String, Any]] = for (tsvLine <- lines.tail) yield {
      val values = tsvLine.split("\t")
      require(header.length == values.length, "Number of columns is not the same as the header")
      val sample = values(sampleColumn)
      val library = if (libraryColumn != -1) Some(values(libraryColumn)) else None

      //FIXME: this is a workaround, should be removed after fixing #180
      if (sample.head.isDigit || library.forall(_.head.isDigit))
        throw new IllegalStateException("Sample or library may not start with a number")

      if (sampleLibCache.contains((sample, library)))
        throw new IllegalStateException(s"Combination of $sample ${library.map("and " + _).getOrElse("")} is found multiple times")
      else sampleLibCache.add((sample, library))
      val valuesMap = (for (
        t <- 0 until values.size if !values(t).isEmpty && t != sampleColumn && t != libraryColumn
      ) yield header(t) -> values(t)).toMap
      library match {
        case Some(lib) => Map("samples" -> Map(sample -> Map("libraries" -> Map(lib -> valuesMap))))
        case _         => Map("samples" -> Map(sample -> valuesMap))
      }
    }
    librariesValues.foldLeft(Map[String, Any]())((acc, kv) => mergeMaps(acc, kv))
  }

  def stringFromInputs(inputs: List[File]): String = {
    val map = inputs.map(f => mapFromFile(f)).foldLeft(Map[String, Any]())((acc, kv) => mergeMaps(acc, kv))
    mapToJson(map).spaces2
  }
}
