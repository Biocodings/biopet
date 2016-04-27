/**
 * Due to the license issue with GATK, this part of Biopet can only be used inside the
 * LUMC. Please refer to https://git.lumc.nl/biopet/biopet/wikis/home for instructions
 * on how to use this protected part of biopet or contact us at sasc@lumc.nl
 */
package nl.lumc.sasc.biopet.extensions.gatk.broad

import java.io.File

import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.extensions.gatk.{ GATKScatterFunction, LocusScatterFunction, TaggedFile }
import nl.lumc.sasc.biopet.core.ScatterGatherableFunction
import nl.lumc.sasc.biopet.utils.VcfUtils
import org.broadinstitute.gatk.utils.commandline.{ Argument, Gather, Input, _ }

class RealignerTargetCreator(val root: Configurable) extends CommandLineGATK with ScatterGatherableFunction {
  def analysis_type = "RealignerTargetCreator"
  scatterClass = classOf[LocusScatterFunction]
  setupScatterFunction = { case scatter: GATKScatterFunction => scatter.includeUnmapped = false }

  /** An output file created by the walker.  Will overwrite contents if file exists */
  @Output(fullName = "out", shortName = "o", doc = "An output file created by the walker.  Will overwrite contents if file exists", required = false, exclusiveOf = "", validation = "")
  @Gather(classOf[org.broadinstitute.gatk.queue.function.scattergather.SimpleTextGatherFunction])
  var out: File = _

  /** Input VCF file with known indels */
  @Input(fullName = "known", shortName = "known", doc = "Input VCF file with known indels", required = false, exclusiveOf = "", validation = "")
  var known: List[File] = config("known", default = Nil)

  /** window size for calculating entropy or SNP clusters */
  @Argument(fullName = "windowSize", shortName = "window", doc = "window size for calculating entropy or SNP clusters", required = false, exclusiveOf = "", validation = "")
  var windowSize: Option[Int] = config("windowSize")

  /** fraction of base qualities needing to mismatch for a position to have high entropy */
  @Argument(fullName = "mismatchFraction", shortName = "mismatch", doc = "fraction of base qualities needing to mismatch for a position to have high entropy", required = false, exclusiveOf = "", validation = "")
  var mismatchFraction: Option[Double] = config("mismatchFraction")

  /** Format string for mismatchFraction */
  @Argument(fullName = "mismatchFractionFormat", shortName = "", doc = "Format string for mismatchFraction", required = false, exclusiveOf = "", validation = "")
  var mismatchFractionFormat: String = "%s"

  /** minimum reads at a locus to enable using the entropy calculation */
  @Argument(fullName = "minReadsAtLocus", shortName = "minReads", doc = "minimum reads at a locus to enable using the entropy calculation", required = false, exclusiveOf = "", validation = "")
  var minReadsAtLocus: Option[Int] = config("minReadsAtLocus")

  /** maximum interval size; any intervals larger than this value will be dropped */
  @Argument(fullName = "maxIntervalSize", shortName = "maxInterval", doc = "maximum interval size; any intervals larger than this value will be dropped", required = false, exclusiveOf = "", validation = "")
  var maxIntervalSize: Option[Int] = config("maxIntervalSize")

  /** Filter out reads with CIGAR containing the N operator, instead of failing with an error */
  @Argument(fullName = "filter_reads_with_N_cigar", shortName = "filterRNC", doc = "Filter out reads with CIGAR containing the N operator, instead of failing with an error", required = false, exclusiveOf = "", validation = "")
  var filter_reads_with_N_cigar: Boolean = config("filter_reads_with_N_cigar", default = false)

  /** Filter out reads with mismatching numbers of bases and base qualities, instead of failing with an error */
  @Argument(fullName = "filter_mismatching_base_and_quals", shortName = "filterMBQ", doc = "Filter out reads with mismatching numbers of bases and base qualities, instead of failing with an error", required = false, exclusiveOf = "", validation = "")
  var filter_mismatching_base_and_quals: Boolean = config("filter_mismatching_base_and_quals", default = false)

  /** Filter out reads with no stored bases (i.e. '*' where the sequence should be), instead of failing with an error */
  @Argument(fullName = "filter_bases_not_stored", shortName = "filterNoBases", doc = "Filter out reads with no stored bases (i.e. '*' where the sequence should be), instead of failing with an error", required = false, exclusiveOf = "", validation = "")
  var filter_bases_not_stored: Boolean = config("", default = false)

  if (config.contains("dbsnp")) known :+= new File(config("dbsnp").asString)

  override def beforeGraph() {
    super.beforeGraph()
    deps ++= known.filter(orig => orig != null && (!orig.getName.endsWith(".list"))).map(orig => VcfUtils.getVcfIndexFile(orig))
  }

  override def cmdLine = super.cmdLine +
    optional("-o", out, spaceSeparated = true, escape = true, format = "%s") +
    repeat("-known", known, formatPrefix = TaggedFile.formatCommandLineParameter, spaceSeparated = true, escape = true, format = "%s") +
    optional("-window", windowSize, spaceSeparated = true, escape = true, format = "%s") +
    optional("-mismatch", mismatchFraction, spaceSeparated = true, escape = true, format = mismatchFractionFormat) +
    optional("-minReads", minReadsAtLocus, spaceSeparated = true, escape = true, format = "%s") +
    optional("-maxInterval", maxIntervalSize, spaceSeparated = true, escape = true, format = "%s") +
    conditional(filter_reads_with_N_cigar, "-filterRNC", escape = true, format = "%s") +
    conditional(filter_mismatching_base_and_quals, "-filterMBQ", escape = true, format = "%s") +
    conditional(filter_bases_not_stored, "-filterNoBases", escape = true, format = "%s")
}

object RealignerTargetCreator {
  def apply(root: Configurable, input: File, outputDir: File): RealignerTargetCreator = {
    val re = new RealignerTargetCreator(root)
    re.input_file :+= input
    re.out = new File(outputDir, input.getName.stripSuffix(".bam") + ".realign.intervals")
    re
  }
}
