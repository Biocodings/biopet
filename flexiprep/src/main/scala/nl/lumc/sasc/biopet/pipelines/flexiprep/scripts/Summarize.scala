package nl.lumc.sasc.biopet.pipelines.flexiprep.scripts

import nl.lumc.sasc.biopet.core._
import nl.lumc.sasc.biopet.core.config._
import nl.lumc.sasc.biopet.function.PythonCommandLineFunction
import org.broadinstitute.sting.commandline._
import java.io.File

class Summarize(val globalConfig: Config, val configPath: List[String]) extends PythonCommandLineFunction {
  setPythonScript("__init__.py", "pyfastqc/")
  setPythonScript("summarize_flexiprep.py")
  
  @Output(doc="Output file", shortName="out", required=true)
  var out: File = _
  
  var samplea: String = _
  var sampleb: String = _
  var runDir: String = _
  var samplename: String = _
  var trim: Boolean = true
  var clip: Boolean = true
  
  def cmdLine = {
    var mode: String = ""
    if (clip) mode += "clip"
    if (trim) mode += "trim"
    if (mode.isEmpty) mode = "none"
    
    getPythonCommand + 
    optional("--run-dir", runDir) +
    optional("--sampleb", sampleb) +
    required(samplename) + 
    required(mode) + 
    required(samplea) + 
    required(out)
  }
}