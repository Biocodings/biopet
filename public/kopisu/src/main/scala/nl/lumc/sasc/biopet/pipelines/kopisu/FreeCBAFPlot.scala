package nl.lumc.sasc.biopet.pipelines.kopisu

import java.io.File

import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.extensions.RscriptCommandLineFunction
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }

class FreeCBAFPlot(val root: Configurable) extends RscriptCommandLineFunction {
  setScript("freec_BAFPlot.R")

  @Input(doc = "Output file from FreeC. *_BAF.txt")
  var input: File = null

  @Output(doc = "Destination for the PNG file")
  var output: File = null

  /* cmdLine to execute R-script and with arguments
   * Arguments should be pasted in the same order as the script is expecting it.
   * Unless some R library is used for named arguments
   * */
  override def cmdLine: String = {

    addArgument("i", input.getAbsolutePath)
    addArgument("o", output.getAbsolutePath)

    super.cmdLine
  }

}
