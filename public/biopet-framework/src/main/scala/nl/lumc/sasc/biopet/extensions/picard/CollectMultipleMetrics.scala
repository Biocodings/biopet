package nl.lumc.sasc.biopet.extensions.picard

import java.io.File

import nl.lumc.sasc.biopet.core.BiopetQScript
import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.core.summary.{ Summarizable, SummaryQScript }
import org.broadinstitute.gatk.utils.commandline.{ Argument, Input, Output }

/**
 * Extension for piacrd's CollectMultipleMetrics tool
 *
 * Created by pjvan_thof on 4/16/15.
 */
class CollectMultipleMetrics(val root: Configurable) extends Picard with Summarizable {
  import CollectMultipleMetrics._

  javaMainClass = new picard.analysis.CollectMultipleMetrics().getClass.getName

  override def defaultCoreMemory = 6.0

  @Input(doc = "The input SAM or BAM files to analyze", required = true)
  var input: File = null

  @Output(doc = "Base name of output files", required = true)
  var outputName: File = null

  @Argument(doc = "Base name of output files", required = true)
  var program: List[String] = config("metrics_programs",
    default = Programs.values.iterator.toList.map(_.toString))

  @Argument(doc = "Assume alignment file is sorted by position", required = false)
  var assumeSorted: Boolean = config("assume_sorted", default = false)

  @Argument(doc = "Stop after processing N reads", required = false)
  var stopAfter: Option[Long] = config("stop_after")

  @Output
  protected var outputFiles: List[File] = Nil

  override def beforeGraph(): Unit = {
    program.foreach {
      case p if p == Programs.CollectAlignmentSummaryMetrics.toString =>
        outputFiles :+= new File(outputName + ".alignment_summary_metrics")
      case p if p == Programs.CollectInsertSizeMetrics.toString =>
        outputFiles :+= new File(outputName + ".insert_size_metrics")
        outputFiles :+= new File(outputName + ".insert_size_histogram.pdf")
      case p if p == Programs.QualityScoreDistribution.toString =>
        outputFiles :+= new File(outputName + ".quality_distribution_metrics")
        outputFiles :+= new File(outputName + ".test.quality_distribution.pdf")
      case p if p == Programs.MeanQualityByCycle.toString =>
        outputFiles :+= new File(outputName + ".quality_by_cycle_metrics")
        outputFiles :+= new File(outputName + ".quality_by_cycle.pdf")
      case p if p == Programs.CollectBaseDistributionByCycle.toString =>
        outputFiles :+= new File(outputName + ".base_distribution_by_cycle_metrics")
        outputFiles :+= new File(outputName + ".base_distribution_by_cycle.pdf")
      case p => BiopetQScript.addError("Program '" + p + "' does not exist for 'CollectMultipleMetrics'")
    }
  }

  override def commandLine = super.commandLine +
    required("INPUT=", input, spaceSeparated = false) +
    required("OUTPUT=", outputName, spaceSeparated = false) +
    conditional(assumeSorted, "ASSUME_SORTED=true") +
    optional("STOP_AFTER=", stopAfter, spaceSeparated = false) +
    repeat("PROGRAM=", program, spaceSeparated = false)

  override def addToQscriptSummary(qscript: SummaryQScript, name: String): Unit = {
    program.foreach(p => {
      val stats: Any = p match {
        case _ if p == Programs.CollectAlignmentSummaryMetrics.toString =>
          Picard.getMetrics(new File(outputName + ".alignment_summary_metrics"), groupBy = Some("CATEGORY"))
        case _ if p == Programs.CollectInsertSizeMetrics.toString =>
          Map(
            "metrics" -> Picard.getMetrics(new File(outputName + ".insert_size_metrics")),
            "histogram" -> Picard.getHistogram(new File(outputName + ".insert_size_metrics"))
          )
        case _ if p == Programs.QualityScoreDistribution.toString =>
          Picard.getHistogram(new File(outputName + ".quality_distribution_metrics"))
        case _ if p == Programs.MeanQualityByCycle.toString =>
          Picard.getHistogram(new File(outputName + ".quality_by_cycle_metrics"))
        case _ if p == Programs.CollectBaseDistributionByCycle.toString =>
          Picard.getHistogram(new File(outputName + ".base_distribution_by_cycle_metrics"), tag = "METRICS CLASS")
        case _ => None
      }
      val sum = new Summarizable {
        override def summaryStats = stats
        override def summaryFiles: Map[String, File] = Map()
      }
      qscript.addSummarizable(sum, p)
    })

  }

  def summaryStats = Map()

  def summaryFiles = {
    program.map {
      case p if p == Programs.CollectInsertSizeMetrics.toString =>
        Map(
          "insert_size_histogram" -> new File(outputName + ".insert_size_histogram.pdf"),
          "insert_size_metrics" -> new File(outputName + ".insert_size_metrics"))
      case otherwise => Map()
    }.foldLeft(Map.empty[String, File]) { case (acc, m) => acc ++ m }
  }
}

object CollectMultipleMetrics {
  object Programs extends Enumeration {
    val CollectAlignmentSummaryMetrics, CollectInsertSizeMetrics, QualityScoreDistribution, MeanQualityByCycle, CollectBaseDistributionByCycle = Value
  }
}