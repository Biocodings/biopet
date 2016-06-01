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
package nl.lumc.sasc.biopet.pipelines.gentrap.measures

import nl.lumc.sasc.biopet.core.annotations.AnnotationGtf
import nl.lumc.sasc.biopet.extensions.HtseqCount
import nl.lumc.sasc.biopet.extensions.picard.SortSam
import nl.lumc.sasc.biopet.extensions.samtools.SamtoolsSort
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.QScript

/**
 * Created by pjvan_thof on 1/12/16.
 */
class FragmentsPerGene(val root: Configurable) extends QScript with Measurement with AnnotationGtf {
  def mergeArgs = MergeArgs(idCols = List(1), valCol = 2, numHeaderLines = 0, fallback = "0")

  override def fixedValues: Map[String, Any] = Map("htseqcount" -> Map("order" -> ""))

  lazy val sortOnId: Boolean = config("sort_on_id", default = true)

  /** Pipeline itself */
  def biopetScript(): Unit = {
    val jobs = bamFiles.map {
      case (id, file) =>

        val bamFile = if (sortOnId) {
          val samtoolsSort = new SortSam(this)
          samtoolsSort.input = file
          samtoolsSort.output = swapExt(outputDir, file, ".bam", ".idsorted.bam")
          samtoolsSort.sortOrder = "queryname"
          samtoolsSort.isIntermediate = true
          add(samtoolsSort)
          samtoolsSort.output
        } else file

        val job = new HtseqCount(this)
        job.inputAnnotation = annotationGtf
        job.inputAlignment = bamFile
        job.output = new File(outputDir, s"$id.$name.counts")
        job.format = Option("bam")
        job.order = if (sortOnId) Some("name") else Some("pos")
        add(job)
        id -> job
    }

    addMergeTableJob(jobs.values.map(_.output).toList, mergedTable, "fragments_per_gene", s".$name.counts")
    addHeatmapJob(mergedTable, heatmap, "fragments_per_gene")

    addSummaryJobs()
  }

  def mergedTable = new File(outputDir, s"$name.fragments_per_gene.tsv")
  def heatmap = new File(outputDir, s"$name.fragments_per_gene.png")
}
