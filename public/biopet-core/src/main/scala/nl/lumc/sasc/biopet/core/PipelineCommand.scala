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
package nl.lumc.sasc.biopet.core

import java.io.{ File, PrintWriter }

import nl.lumc.sasc.biopet.utils.config.Config
import nl.lumc.sasc.biopet.core.workaround.BiopetQCommandLine
import nl.lumc.sasc.biopet.utils.{ MainCommand, Logging }
import org.apache.log4j.{ PatternLayout, WriterAppender }
import org.broadinstitute.gatk.queue.util.{ Logging => GatkLogging }

/** Wrapper around executable from Queue */
trait PipelineCommand extends MainCommand with GatkLogging {

  /**
   * Gets location of compiled class of pipeline
   * @return path from classPath to class file
   */
  def pipeline = "/" + getClass.getName.stripSuffix("$").replaceAll("\\.", "/") + ".class"

  /** Class can be used directly from java with -cp option */
  def main(args: Array[String]): Unit = {
    val argsSize = args.length
    for (t <- 0 until argsSize) {
      if (args(t) == "-config" || args(t) == "--config_file") {
        if (args.length <= (t + 1)) throw new IllegalStateException("-config needs a value: <file>")
        Config.global.loadConfigFile(new File(args(t + 1)))
      }

      if (args(t) == "-cv" || args(t) == "--config_value") {
        if (args.length <= (t + 1)) throw new IllegalStateException("-cv needs a value: <'key=value' or 'path:path:key=value'>")
        val v = args(t + 1).split("=")
        require(v.size == 2, "Value should be formatted like 'key=value' or 'path:path:key=value'")
        val value = v(1)
        val p = v(0).split(":")
        val key = p.last
        val path = p.dropRight(1).toList
        Config.global.addValue(key, value, path)
      }

      if (args(t) == "--logging_level" || args(t) == "-l") {
        if (args.length <= (t + 1)) throw new IllegalStateException("--logging_level/-l needs a value: <debug/info/warn/error>")
        args(t + 1).toLowerCase match {
          case "debug" => Logging.logger.setLevel(org.apache.log4j.Level.DEBUG)
          case "info"  => Logging.logger.setLevel(org.apache.log4j.Level.INFO)
          case "warn"  => Logging.logger.setLevel(org.apache.log4j.Level.WARN)
          case "error" => Logging.logger.setLevel(org.apache.log4j.Level.ERROR)
          case _       => throw new IllegalStateException("--logging_level/-l needs a value: <debug/info/warn/error>")
        }
      }
    }
    for (t <- 0 until argsSize) {
      if (args(t) == "--outputDir" || args(t) == "-outDir") {
        throw new IllegalArgumentException("Commandline argument is deprecated, should use config for this now or use: -cv output_dir=<Path to output dir>")
      }
    }

    val logFile = {
      val pipelineName = this.getClass.getSimpleName.toLowerCase.split("""\$""").head
      val pipelineConfig = Config.global.map.getOrElse(pipelineName, Map()).asInstanceOf[Map[String, Any]]
      val pipelineOutputDir = new File(Config.global.map.getOrElse("output_dir", pipelineConfig.getOrElse("output_dir", "./")).toString)
      val logDir: File = new File(pipelineOutputDir, ".log")
      logDir.mkdirs()
      new File(logDir, "biopet." + BiopetQCommandLine.timestamp + ".log")
    }

    val a = new WriterAppender(new PatternLayout("%-5p [%d] [%C{1}] - %m%n"), new PrintWriter(logFile))
    logger.addAppender(a)

    var argv: Array[String] = Array()
    argv ++= Array("-S", pipeline)
    argv ++= args
    if (!args.contains("--log_to_file") && !args.contains("-log")) {
      argv ++= List("--log_to_file", new File(logFile.getParentFile, "queue." + BiopetQCommandLine.timestamp + ".log").getAbsolutePath)
    }
    BiopetQCommandLine.main(argv)
  }
}