package nl.lumc.sasc.biopet.extensions.bowtie

import java.io.File

import nl.lumc.sasc.biopet.core.{ Version, BiopetCommandLineFunction }
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Argument, Input }

/**
 * Created by pjvan_thof on 8/15/15.
 */
class BowtieBuild(val root: Configurable) extends BiopetCommandLineFunction with Version {
  @Input(required = true)
  var reference: File = _

  @Argument(required = true)
  var baseName: String = _

  executable = config("exe", default = "bowtie-build", freeVar = false)
  def versionRegex = """.*[Vv]ersion:? (\d*\.\d*\.\d*)""".r
  def versionCommand = executable + " --version"

  override def defaultCoreMemory = 15.0

  override def beforeGraph: Unit = {
    outputFiles ::= new File(reference.getParentFile, baseName + ".1.ebwt")
    outputFiles ::= new File(reference.getParentFile, baseName + ".2.ebwt")
  }

  def cmdLine = required("cd", reference.getParentFile) + " && " +
    required(executable) +
    required(reference) +
    required(baseName)
}