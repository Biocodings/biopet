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
package nl.lumc.sasc.biopet.utils.rscript

import java.io.{ File, FileOutputStream }

import nl.lumc.sasc.biopet.utils.Logging
import nl.lumc.sasc.biopet.utils.config.Configurable

import scala.sys.process.{ Process, ProcessLogger }

/**
 * Created by pjvanthof on 13/09/15.
 */
trait Rscript extends Configurable {
  protected var script: File

  def rscriptExecutable: String = config("exe", default = "Rscript", submodule = "Rscript")

  /** This is the defaul implementation, to add arguments override this */
  def cmd: Seq[String] = Seq(rscriptExecutable, script.getAbsolutePath)

  /**
   * If script not exist in file system it try to copy it from the jar
   * @param dir Directory to store temp script, if None or not given File.createTempFile is called
   */
  protected def checkScript(dir: Option[File] = None): Unit = {
    if (script.exists()) {
      script = script.getAbsoluteFile
    } else {
      val rScript: File = dir match {
        case Some(dir) => new File(dir, script.getName)
        case _ => {
          val file = File.createTempFile(script.getName, ".R")
          file.deleteOnExit()
          file
        }
      }
      if (!rScript.getAbsoluteFile.getParentFile.exists) rScript.getParentFile.mkdirs

      val is = getClass.getResourceAsStream(script.getPath)
      val os = new FileOutputStream(rScript)

      org.apache.commons.io.IOUtils.copy(is, os)
      os.close()

      script = rScript
    }
  }

  /**
   * Execute rscript on local system
   * @param logger How to handle stdout and stderr
   */
  def runLocal(logger: ProcessLogger): Unit = {
    checkScript()

    Logging.logger.info("Running: " + cmd.mkString(" "))

    val process = Process(cmd).run(logger)
    Logging.logger.info(process.exitValue())
  }

  /**
   * Execute rscript on local system
   * Stdout and stderr will go to biopet logger
   */
  def runLocal(): Unit = {
    runLocal(ProcessLogger(Logging.logger.info(_)))
  }
}
