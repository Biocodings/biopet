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

import java.io.File

import nl.lumc.sasc.biopet.core.summary.{SummaryQScript, WriteSummary}
import nl.lumc.sasc.biopet.utils.config.Configurable
import nl.lumc.sasc.biopet.core.report.ReportBuilderExtension
import nl.lumc.sasc.biopet.core.workaround.BiopetQCommandLine
import nl.lumc.sasc.biopet.utils.Logging
import org.broadinstitute.gatk.queue.{QScript, QSettings}
import org.broadinstitute.gatk.queue.function.QFunction
import org.broadinstitute.gatk.queue.util.{Logging => GatkLogging}

/** Base for biopet pipeline */
trait BiopetQScript extends Configurable with GatkLogging { qscript: QScript =>

  @Argument(doc = "JSON / YAML config file(s)", fullName = "config_file", shortName = "config", required = false)
  val configfiles: List[File] = Nil

  @Argument(doc = "Config values, value should be formatted like 'key=value' or 'namespace:namespace:key=value'", fullName = "config_value", shortName = "cv", required = false)
  val configValues: List[String] = Nil

  /** Output directory of pipeline */
  var outputDir: File = {
    if (config.contains("output_dir", path = Nil)) config("output_dir", path = Nil).asFile
    else new File(".")
  }

  @Argument(doc = "Disable all scatters", shortName = "DSC", required = false)
  var disableScatter: Boolean = false

  var outputFiles: Map[String, File] = Map()

  type InputFile = BiopetQScript.InputFile

  var inputFiles: List[InputFile] = Nil

  /** Get implemented from org.broadinstitute.gatk.queue.QScript */
  var qSettings: QSettings

  /** Get implemented from org.broadinstitute.gatk.queue.QScript */
  var functions: Seq[QFunction]

  /** Init for pipeline */
  def init()

  /** Pipeline itself */
  def biopetScript()

  /** Returns the extension to make the report */
  def reportClass: Option[ReportBuilderExtension] = None

  val skipWriteDependencies: Boolean = config("skip_write_dependencies", default = false)

  /** Script from queue itself, final to force some checks for each pipeline and write report */
  final def script() {
    outputDir = config("output_dir")
    outputDir = outputDir.getAbsoluteFile

    BiopetQScript.checkOutputDir(outputDir)

    init()
    biopetScript()
    logger.info("Biopet script done")

    if (disableScatter) {
      logger.info("Disable scatters")
      for (function <- functions) function match {
        case f: ScatterGatherableFunction => f.scatterCount = 1
        case _                            =>
      }
    }

    this match {
      case q: MultiSampleQScript if q.onlySamples.nonEmpty && !q.samples.forall(x => q.onlySamples.contains(x._1)) =>
        logger.info("Write report is skipped because sample flag is used")
      case _ => reportClass.foreach { report =>
        add(report)
      }
    }

    logger.info("Running pre commands")
    for (function <- functions) function match {
      case f: BiopetCommandLineFunction =>
        f.preProcessExecutable()
        f.beforeGraph()
        f.internalBeforeGraph()
        f.commandLine
      case f: WriteSummary => f.init()
      case _               =>
    }

    if (outputDir.getParentFile.canWrite || (outputDir.exists && outputDir.canWrite))
      globalConfig.writeReport(qSettings.runName, new File(outputDir, ".log/" + qSettings.runName))
    else Logging.addError("Parent of output dir: '" + outputDir.getParent + "' is not writeable, output directory cannot be created")

    logger.info("Checking input files")
    inputFiles.par.foreach { i =>
      if (!i.file.exists()) Logging.addError(s"Input file does not exist: ${i.file}")
      if (!i.file.canRead) Logging.addError(s"Input file can not be read: ${i.file}")
      if (!i.file.isAbsolute) Logging.addError(s"Input file should be an absolute path: ${i.file}")
    }

    functions.filter(_.jobOutputFile == null).foreach(f => {
      try {
        val className = if (f.getClass.isAnonymousClass) f.getClass.getSuperclass.getSimpleName else f.getClass.getSimpleName
        f.jobOutputFile = new File(f.firstOutput.getAbsoluteFile.getParent, "." + f.firstOutput.getName + "." + className + ".out")
      } catch {
        case e: NullPointerException => logger.warn(s"Can't generate a jobOutputFile for $f")
      }
    })

    if (!skipWriteDependencies) WriteDependencies.writeDependencies(
      functions,
      new File(outputDir, ".log" + File.separator + BiopetQCommandLine.timestamp + File.separator + "graph"))

    Logging.checkErrors()
    logger.info("Script complete without errors")
  }

  /** Get implemented from org.broadinstitute.gatk.queue.QScript */
  def add(functions: QFunction*)

  /** Get implemented from org.broadinstitute.gatk.queue.QScript */
  def addAll(functions: scala.Traversable[org.broadinstitute.gatk.queue.function.QFunction])

  /** Function to set isIntermediate and add in 1 line */
  def add(function: QFunction, isIntermediate: Boolean = false) {
    function.isIntermediate = isIntermediate
    add(function)
  }

  def add(subPipeline: QScript): Unit = {
    subPipeline.qSettings = this.qSettings
    subPipeline match {
      case that: SummaryQScript =>
        that.init()
        that.biopetScript()
        this match {
          case s: SummaryQScript => s.addSummaryQScript(that)
          case _                 =>
        }
      case that: BiopetQScript =>
        that.init()
        that.biopetScript()
      case _ => subPipeline.script
    }
    addAll(subPipeline.functions)
  }
}

object BiopetQScript {
  case class InputFile(file: File, md5: Option[String] = None)

  def checkOutputDir(outputDir: File): Unit = {
    // Sanity checks
    require(outputDir.getAbsoluteFile.getParentFile.canRead, s"No premision to read parent of outputdir: ${outputDir.getParentFile}")
    require(outputDir.getAbsoluteFile.getParentFile.canWrite, s"No premision to write parent of outputdir: ${outputDir.getParentFile}")
    outputDir.mkdir()
    require(outputDir.getAbsoluteFile.canRead, s"No premision to read outputdir: $outputDir")
    require(outputDir.getAbsoluteFile.canWrite, s"No premision to write outputdir: $outputDir")
  }


}
