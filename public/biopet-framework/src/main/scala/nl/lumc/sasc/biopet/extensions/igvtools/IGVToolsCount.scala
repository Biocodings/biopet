
package nl.lumc.sasc.biopet.extensions.igvtools

import java.nio.file.InvalidPathException

import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output, Argument }
import java.io.{ FileNotFoundException, File }

/**
 * IGVTools `count` wrapper
 *
 * @constructor create a new IGVTools instance from a `.bam` file
 *
 */

class IGVToolsCount(val root: Configurable) extends IGVTools {
  @Input(doc = "Bam File")
  var input: File = _

  @Input(doc = "<genome>.chrom.sizes File")
  var genomeChromSizes: File = _

  @Output
  var tdf: Option[File] = _

  @Output
  var wig: Option[File] = _

  var maxZoom: Option[Int] = config("maxZoom")
  var windowSize: Option[Int] = config("windowSize")
  var extFactor: Option[Int] = config("extFactor")

  var preExtFactor: Option[Int] = config("preExtFactor")
  var postExtFactor: Option[Int] = config("postExtFactor")

  var windowFunctions: Option[String] = config("windowFunctions")
  var strands: Option[String] = config("strands")
  var bases: Boolean = config("bases", default = false)

  var query: Option[String] = config("query")
  var minMapQuality: Option[Int] = config("minMapQuality")
  var includeDuplicates: Boolean = config("includeDuplicates", default = false)

  var pairs: Boolean = config("pairs", default = false)

  override def afterGraph {
    super.afterGraph
    if (!input.exists()) throw new FileNotFoundException("Input bam is required for IGVToolsCount")

    if (!wig.isEmpty && !wig.get.getAbsolutePath.endsWith(".wig")) throw new IllegalArgumentException("Wiggle file should have a .wig file-extension")
    if (!tdf.isEmpty && !tdf.get.getAbsolutePath.endsWith(".tdf")) throw new IllegalArgumentException("TDF file should have a .tdf file-extension")
  }

  def cmdLine = {
    required(executable) +
      required("count") +
      optional("--maxZoom", maxZoom) +
      optional("--windowSize", windowSize) +
      optional("--extFactor", extFactor) +
      optional("--preExtFactor", preExtFactor) +
      optional("--postExtFactor", postExtFactor) +
      optional("--windowFunctions", windowFunctions) +
      optional("--strands", strands) +
      conditional(bases, "--bases") +
      optional("--query", query) +
      optional("--minMapQuality", minMapQuality) +
      conditional(includeDuplicates, "--includeDuplicates") +
      conditional(pairs, "--pairs") +
      required(input) +
      required(outputArg) +
      required(genomeChromSizes)
  }

  /**
   * This part should never fail, these values are set within this wrapper
   *
   */
  private def outputArg: String = {
    (tdf, wig) match {
      case (None, None)       => throw new IllegalArgumentException("Either TDF or WIG should be supplied");
      case (Some(a), None)    => a.getAbsolutePath;
      case (None, Some(b))    => b.getAbsolutePath;
      case (Some(a), Some(b)) => a.getAbsolutePath + "," + b.getAbsolutePath;
    }
  }
}

object IGVToolsCount {
  /**
   * Create an object by specifying the `input` (.bam),
   * and the `genomename` (hg18,hg19,mm10)
   *
   * @param input Bamfile to count reads from
   * @return a new IGVToolsCount instance
   * @throws FileNotFoundException bam File is not found
   * @throws IllegalArgumentException tdf or wig not supplied
   */
  def apply(root: Configurable, input: File, genomeChromSizes: File): IGVToolsCount = {
    val counting = new IGVToolsCount(root)
    counting.input = input
    counting.genomeChromSizes = genomeChromSizes
    return counting
  }
}