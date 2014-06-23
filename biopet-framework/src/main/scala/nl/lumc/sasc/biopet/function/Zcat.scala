package nl.lumc.sasc.biopet.function

import nl.lumc.sasc.biopet.core._
import nl.lumc.sasc.biopet.core.config._
//import org.broadinstitute.sting.queue.function.CommandLineFunction
import org.broadinstitute.sting.commandline._
import java.io.File

class Zcat(val root:Configurable) extends BiopetCommandLineFunction {
  @Input(doc="Zipped file")
  var input: File = _
  
  @Output(doc="Unzipped file")
  var output: File = _
  
  executeble = config("exe", "zcat")
  
  def cmdLine = required(executeble) + required(input) + " > " + required(output)
}