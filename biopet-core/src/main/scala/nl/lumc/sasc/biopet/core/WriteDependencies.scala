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
 * A dual licensing mode is applied. The source code within this project is freely available for non-commercial use under an AGPL
 * license; For commercial users or users who do not want to follow the AGPL
 * license, please contact us to obtain a separate license.
 */
package nl.lumc.sasc.biopet.core

import java.io.{ File, PrintWriter }

import nl.lumc.sasc.biopet.core.summary.WriteSummary
import nl.lumc.sasc.biopet.utils.config.Configurable
import nl.lumc.sasc.biopet.utils.{ ConfigUtils, Logging }
import org.broadinstitute.gatk.queue.function.{ CommandLineFunction, QFunction }

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * This object will generate with [[WriteDependencies.writeDependencies]] a json file where information about job and file dependencies are stored
 *
 * @author Peter van 't Hof <p.j.van_t_hof@lumc.nl>
 */
object WriteDependencies extends Logging with Configurable {
  val root: Configurable = null
  private def createFunctionNames(functions: Seq[QFunction]): Map[QFunction, String] = {
    val cache: mutable.Map[String, Int] = mutable.Map()
    (for (function <- functions) yield {
      val baseName = function match {
        case f: Configurable => f.configNamespace
        case f               => f.getClass.getSimpleName
      }
      cache += baseName -> (cache.getOrElse(baseName, 0) + 1)
      function -> s"$baseName-${cache(baseName)}"
    }).toMap
  }

  /**
   * This method will generate a json file where information about job and file dependencies are stored
   *
   * @param functions This should be all functions that are given to the graph of Queue
   * @param outputFile Json file to write dependencies to
   */
  def writeDependencies(functions: Seq[QFunction], outputFile: File, mainJobsFile: Option[File] = None): Unit = {
    logger.info("Start calculating dependencies")

    val errorOnMissingInput: Boolean = config("error_on_missing_input", false)

    val functionNames = createFunctionNames(functions)

    case class QueueFile(file: File) {
      private val inputJobs: ListBuffer[QFunction] = ListBuffer()
      def addInputJob(function: QFunction) = inputJobs += function
      def inputJobNames = inputJobs.toList.map(functionNames)

      private val outputJobs: ListBuffer[QFunction] = ListBuffer()
      def addOutputJob(function: QFunction) = {
        if (outputJobs.nonEmpty) logger.warn(s"File '$file' is found as output of multiple jobs")
        outputJobs += function
      }
      def outputJobNames = outputJobs.toList.map(functionNames)

      def getMap = {
        val fileExist = file.exists()
        if (!fileExist && outputJobs.isEmpty) {
          if (errorOnMissingInput) Logging.addError(s"Input file does not exist: $file")
          else logger.warn(s"Input file does not exist: $file")
        }
        Map(
          "path" -> file.getAbsolutePath,
          "intermediate" -> isIntermediate,
          "output_jobs" -> outputJobNames,
          "input_jobs" -> inputJobNames,
          "exists_at_start" -> fileExist,
          "pipeline_input" -> outputJobs.isEmpty
        )
      }

      def isIntermediate = outputJobs.exists(_.isIntermediate)
    }

    val files: mutable.Map[File, QueueFile] = mutable.Map()

    def outputFiles(function: QFunction) = {
      if (function.jobErrorFile == null) function.outputs :+ function.jobOutputFile
      else function.outputs :+ function.jobOutputFile :+ function.jobErrorFile
    }

    for (function <- functions) {
      for (input <- function.inputs) {
        val file = files.getOrElse(input, QueueFile(input))
        file.addInputJob(function)
        files += input -> file
      }
      for (output <- outputFiles(function)) {
        val file = files.getOrElse(output, QueueFile(output))
        file.addOutputJob(function)
        files += output -> file
      }
    }

    val jobs = functionNames.par.map {
      case (f, name) =>
        name -> Map("command" -> (f match {
          case cmd: CommandLineFunction => cmd.commandLine
          case _                        => None
        }), "main_job" -> (f match {
          case cmd: BiopetCommandLineFunction => cmd.mainFunction
          case _: WriteSummary                => true
          case _                              => false
        }), "intermediate" -> f.isIntermediate,
          "depends_on_intermediate" -> f.inputs.exists(files(_).isIntermediate),
          "depends_on_jobs" -> f.inputs.toList.flatMap(files(_).outputJobNames).distinct,
          "output_used_by_jobs" -> outputFiles(f).toList.flatMap(files(_).inputJobNames).distinct,
          "outputs" -> outputFiles(f).toList,
          "inputs" -> f.inputs.toList,
          "done_at_start" -> f.isDone,
          "fail_at_start" -> f.isFail)
    }.toIterator.toMap

    mainJobsFile.foreach { file =>
      val mainJobs = jobs.filter(_._2("main_job") == true).map {
        case (name, job) =>
          name -> (job + ("depends_on_jobs" -> getMainDependencies(name, jobs)))
      }

      val writer = new PrintWriter(file)
      writer.println(ConfigUtils.mapToJson(mainJobs).spaces2)
      writer.close()
    }

    logger.info(s"Writing dependencies to: $outputFile")
    val writer = new PrintWriter(outputFile)
    writer.println(ConfigUtils.mapToJson(Map(
      "jobs" -> jobs,
      "files" -> files.values.par.map(_.getMap).toList
    )).spaces2)
    writer.close()

    logger.info("done calculating dependencies")
  }

  def getMainDependencies(jobName: String, jobsMap: Map[String, Map[String, Any]]): List[String] = {
    val job = jobsMap(jobName)
    val dependencies = job("depends_on_jobs") match {
      case l: List[_] => l.map(_.toString)
    }
    dependencies.flatMap { dep =>
      jobsMap(dep)("main_job") match {
        case true  => List(dep)
        case false => getMainDependencies(dep, jobsMap)
      }
    }
  }
}
