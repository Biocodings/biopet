package nl.lumc.sasc.biopet.pipelines.bammetrics

import java.io.{ PrintWriter, File }

import nl.lumc.sasc.biopet.core.report.{ ReportBuilder, ReportPage, ReportSection }
import nl.lumc.sasc.biopet.core.summary.{ SummaryValue, Summary }
import nl.lumc.sasc.biopet.extensions.rscript.StackedBarPlot

/**
 * Created by pjvan_thof on 3/30/15.
 */
object BammetricsReport extends ReportBuilder {
  // FIXME: Not yet finished

  val reportName = "Bam Metrics"

  def indexPage = ReportPage(Map(
    "Bam Metrics" -> bamMetricsPage
  ), List(
    "Report" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/bamMetricsFront.ssp")
  ),
    Map()
  )

  def bamMetricsPage = ReportPage(
    Map(),
    List(
      "Summary" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/alignmentSummary.ssp"),
      "Bam Stats" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/bamStats.ssp"),
      "Insert Size" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/insertSize.ssp"),
      "RNA (optional)" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/rna.ssp"),
      "Target (optional)" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/target.ssp"),
      "GC Bias" -> ReportSection("/nl/lumc/sasc/biopet/pipelines/bammetrics/gcBias.ssp")
    ),
    Map()
  )

  def alignmentSummaryPlot(outputDir: File,
                           prefix: String,
                           summary: Summary,
                           libraryLevel: Boolean = false,
                           sampleId: Option[String] = None): Unit = {
    val tsvFile = new File(outputDir, prefix + ".tsv")
    val pngFile = new File(outputDir, prefix + ".png")
    val tsvWriter = new PrintWriter(tsvFile)
    if (libraryLevel) tsvWriter.print("Library") else tsvWriter.print("Sample")
    tsvWriter.println("\tMapped\tDuplicates\tUnmapped\tSecondary")

    def getLine(summary: Summary, sample: String, lib: Option[String] = None): String = {
      val mapped = new SummaryValue(List("bammetrics", "stats", "biopet_flagstat", "Mapped"),
        summary, Some(sample), lib).value.getOrElse(0).toString.toLong
      val duplicates = new SummaryValue(List("bammetrics", "stats", "biopet_flagstat", "Duplicates"),
        summary, Some(sample), lib).value.getOrElse(0).toString.toLong
      val total = new SummaryValue(List("bammetrics", "stats", "biopet_flagstat", "All"),
        summary, Some(sample), lib).value.getOrElse(0).toString.toLong
      val secondary = new SummaryValue(List("bammetrics", "stats", "biopet_flagstat", "NotPrimaryAlignment"),
        summary, Some(sample), lib).value.getOrElse(0).toString.toLong
      val sb = new StringBuffer()
      if (lib.isDefined) sb.append(sample + "-" + lib.get + "\t") else sb.append(sample + "\t")
      sb.append((mapped - duplicates) + "\t")
      sb.append(duplicates + "\t")
      sb.append((total - mapped - secondary) + "\t")
      sb.append(secondary)
      sb.toString
    }

    if (libraryLevel) {
      for (
        sample <- summary.samples if (sampleId.isEmpty || sample == sampleId.get);
        lib <- summary.libraries(sample)
      ) {
        tsvWriter.println(getLine(summary, sample, Some(lib)))
      }
    } else {
      for (sample <- summary.samples if (sampleId.isEmpty || sample == sampleId.get)) {
        tsvWriter.println(getLine(summary, sample))
      }
    }

    tsvWriter.close()

    val plot = new StackedBarPlot(null)
    plot.input = tsvFile
    plot.output = pngFile
    plot.ylabel = Some("Reads")
    plot.width = Some(750)
    plot.title = Some("Aligned reads")
    plot.runLocal()
  }

}
