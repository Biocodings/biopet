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
package nl.lumc.sasc.biopet.pipelines.gears

import nl.lumc.sasc.biopet.core.summary.SummaryQScript
import nl.lumc.sasc.biopet.core.BiopetQScript.InputFile
import nl.lumc.sasc.biopet.core.{ PipelineCommand, SampleLibraryTag }
import nl.lumc.sasc.biopet.pipelines.flexiprep.Flexiprep
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.QScript

/**
 * Created by wyleung
 */
class GearsSingle(val root: Configurable) extends QScript with SummaryQScript with SampleLibraryTag {
  def this() = this(null)

  @Input(doc = "R1 reads in FastQ format", shortName = "R1", required = false)
  var fastqR1: Option[File] = None

  @Input(doc = "R2 reads in FastQ format", shortName = "R2", required = false)
  var fastqR2: Option[File] = None

  @Input(doc = "All unmapped reads will be extracted from this bam for analysis", shortName = "bam", required = false)
  var bamFile: Option[File] = None

  @Argument(required = false)
  var outputName: String = _

  lazy val krakenScript = if (config("gears_use_kraken", default = true)) Some(new GearsKraken(this)) else None
  lazy val qiimeRatx = if (config("gear_use_qiime_rtax", default = false)) Some(new GearsQiimeRtax(this)) else None
  lazy val qiimeClosed = if (config("gear_use_qiime_closed", default = false)) Some(new GearsQiimeClosed(this)) else None

  /** Executed before running the script */
  def init(): Unit = {
    require(fastqR1.isDefined || bamFile.isDefined, "Please specify fastq-file(s) or bam file")
    require(fastqR1.isDefined != bamFile.isDefined, "Provide either a bam file or a R1/R2 file")

    if (outputName == null) {
      if (fastqR1.isDefined) outputName = fastqR1.map(_.getName
        .stripSuffix(".gz")
        .stripSuffix(".fastq")
        .stripSuffix(".fq"))
        .getOrElse("noName")
      else outputName = bamFile.map(_.getName.stripSuffix(".bam")).getOrElse("noName")
    }

    if (fastqR1.isDefined) {
      fastqR1.foreach(inputFiles :+= InputFile(_))
      fastqR2.foreach(inputFiles :+= InputFile(_))
    } else inputFiles :+= InputFile(bamFile.get)
  }

  override def reportClass = {
    val gears = new GearsSingleReport(this)
    gears.outputDir = new File(outputDir, "report")
    gears.summaryFile = summaryFile
    sampleId.foreach(gears.args += "sampleId" -> _)
    libId.foreach(gears.args += "libId" -> _)
    Some(gears)
  }

  protected def executeFlexiprep(r1: File, r2: Option[File]): (File, Option[File]) = {
    if (!config("skip_flexiprep", default = false).asBoolean) {
      val flexiprep = new Flexiprep(this)
      flexiprep.input_R1 = r1
      flexiprep.input_R2 = r2
      flexiprep.outputDir = new File(outputDir, "flexiprep")
      add(flexiprep)
      (flexiprep.fastqR1Qc, flexiprep.fastqR2Qc)
    } else (r1, r2)
  }

  /** Method to add jobs */
  def biopetScript(): Unit = {
    val (r1, r2): (File, Option[File]) = (fastqR1, fastqR2, bamFile) match {
      case (Some(r1), _, _) => executeFlexiprep(r1, fastqR2)
      case (_, _, Some(bam)) =>
        val extract = new ExtractUnmappedReads(this)
        extract.outputDir = outputDir
        extract.bamFile = bam
        extract.outputName = outputName
        add(extract)
        executeFlexiprep(extract.fastqUnmappedR1, Some(extract.fastqUnmappedR2))
      case _ => throw new IllegalArgumentException("Missing input files")
    }

    krakenScript foreach { kraken =>
      kraken.outputDir = new File(outputDir, "kraken")
      kraken.fastqR1 = r1
      kraken.fastqR2 = r2
      kraken.outputName = outputName
      add(kraken)
    }

    qiimeRatx foreach { qiimeRatx =>
      qiimeRatx.outputDir = new File(outputDir, "qiime_rtax")
      qiimeRatx.fastqR1 = r1
      qiimeRatx.fastqR2 = r2
      add(qiimeRatx)
    }

    qiimeClosed foreach { qiimeClosed =>
      qiimeClosed.outputDir = new File(outputDir, "qiime_closed")
      qiimeClosed.fastqR1 = r1
      qiimeClosed.fastqR2 = r2
      add(qiimeClosed)

      //TODO: Plots
    }

    addSummaryJobs()
  }

  /** Location of summary file */
  def summaryFile = new File(outputDir, sampleId.getOrElse("sampleName_unknown") + ".gears.summary.json")

  /** Pipeline settings shown in the summary file */
  def summarySettings: Map[String, Any] = Map.empty

  /** Statistics shown in the summary file */
  def summaryFiles: Map[String, File] = Map.empty ++
    (if (bamFile.isDefined) Map("input_bam" -> bamFile.get) else Map()) ++
    (if (fastqR1.isDefined) Map("input_R1" -> fastqR1.get) else Map()) ++
    outputFiles
}

/** This object give a default main method to the pipelines */
object GearsSingle extends PipelineCommand