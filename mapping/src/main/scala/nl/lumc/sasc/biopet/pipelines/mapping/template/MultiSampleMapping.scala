package nl.lumc.sasc.biopet.pipelines.mapping.template

import java.io.File

import nl.lumc.sasc.biopet.core.{ Reference, TemplateTool }
import nl.lumc.sasc.biopet.utils.Question

/**
 * Created by pjvanthof on 17/12/2016.
 */
object MultiSampleMapping extends TemplateTool {

  def pipelineName = "MultiSampleMapping"

  override def sampleConfigs: List[File] = TemplateTool.askSampleConfigs()

  def possibleAligners = List("bwa-mem", "bwa-aln", "bowtie", "bowtie2", "gsnap", "hisat2",
    "tophat", "stampy", "star", "star-2pass")

  def pipelineMap(map: Map[String, Any], expert: Boolean): Map[String, Any] = {
    val referenceConfig = map ++ Reference.askReference

    val aligner = if (map.contains("aligner")) map("aligner").toString
    else Question.string("Aligner", posibleValues = possibleAligners, default = Some("bwa-mem"))
    val mappingToGears = Question.string("Reads to process in metagenomics pipeline", posibleValues = List("none", "all", "unmapped"), default = Some("none"))

    referenceConfig ++ Map(
      "aligner" -> aligner,
      "mapping_to_gears" -> mappingToGears
    )
  }
}