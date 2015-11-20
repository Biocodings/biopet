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
package nl.lumc.sasc.biopet.pipelines.shiva

import nl.lumc.sasc.biopet.core.summary.SummaryQScript
import nl.lumc.sasc.biopet.core.{ Reference, SampleLibraryTag }
import nl.lumc.sasc.biopet.extensions.gatk.{ CombineVariants, GenotypeConcordance }
import nl.lumc.sasc.biopet.extensions.tools.VcfStats
import nl.lumc.sasc.biopet.extensions.vt.VtNormalize
import nl.lumc.sasc.biopet.pipelines.bammetrics.TargetRegions
import nl.lumc.sasc.biopet.pipelines.shiva.variantcallers._
import nl.lumc.sasc.biopet.utils.{ BamUtils, Logging }
import org.broadinstitute.gatk.queue.QScript

/**
 * Common trait for ShivaVariantcalling
 *
 * Created by pjvan_thof on 2/26/15.
 */
trait ShivaVariantcallingTrait extends SummaryQScript
  with SampleLibraryTag
  with Reference
  with TargetRegions {
  qscript: QScript =>

  @Input(doc = "Bam files (should be deduped bams)", shortName = "BAM", required = true)
  protected var inputBamsArg: List[File] = Nil

  var inputBams: Map[String, File] = Map()

  /** Executed before script */
  def init(): Unit = {
    if (inputBamsArg.nonEmpty) inputBams = BamUtils.sampleBamMap(inputBamsArg)
  }

  var referenceVcf: Option[File] = config("reference_vcf")

  var referenceVcfRegions: Option[File] = config("reference_vcf_regions")

  /** Name prefix, can override this methods if neeeded */
  def namePrefix: String = {
    (sampleId, libId) match {
      case (Some(s), Some(l)) => s + "-" + l
      case (Some(s), _)       => s
      case _                  => config("name_prefix")
    }
  }

  override def defaults = Map("bcftoolscall" -> Map("f" -> List("GQ")))

  /** Final merged output files of all variantcaller modes */
  def finalFile = new File(outputDir, namePrefix + ".final.vcf.gz")

  /** Variantcallers requested by the config */
  protected val configCallers: Set[String] = config("variantcallers")

  /** This will add jobs for this pipeline */
  def biopetScript(): Unit = {
    for (cal <- configCallers) {
      if (!callersList.exists(_.name == cal))
        Logging.addError("variantcaller '" + cal + "' does not exist, possible to use: " + callersList.map(_.name).mkString(", "))
    }

    val callers = callersList.filter(x => configCallers.contains(x.name)).sortBy(_.prio)

    require(inputBams.nonEmpty, "No input bams found")
    require(callers.nonEmpty, "must select at least 1 variantcaller, choices are: " + callersList.map(_.name).mkString(", "))

    val cv = new CombineVariants(qscript)
    cv.outputFile = finalFile
    cv.setKey = "VariantCaller"
    cv.genotypeMergeOptions = Some("PRIORITIZE")
    cv.rodPriorityList = callers.map(_.name).mkString(",")
    for (caller <- callers) {
      caller.inputBams = inputBams
      add(caller)
      addStats(caller.outputFile, caller.name)
      val normalize: Boolean = config("execute_vt_normalize", default = false)
      if (normalize) {
        val vt = new VtNormalize(this)
        vt.inputVcf = caller.outputFile
        vt.outputVcf = swapExt(caller.outputDir, caller.outputFile, ".vcf.gz", ".normaize.vcf.gz")
        add(vt)
        cv.addInput(vt.outputVcf, caller.name)
      } else cv.addInput(caller.outputFile, caller.name)
    }
    add(cv)

    addStats(finalFile, "final")

    addSummaryJobs()
  }

  protected def addStats(vcfFile: File, name: String): Unit = {
    val vcfStats = new VcfStats(qscript)
    vcfStats.input = vcfFile
    vcfStats.setOutputDir(new File(vcfFile.getParentFile, "vcfstats"))
    vcfStats.infoTags :+= "VariantCaller"
    add(vcfStats)
    addSummarizable(vcfStats, s"$namePrefix-vcfstats-$name")

    referenceVcf.foreach(referenceVcfFile => {
      val gc = new GenotypeConcordance(this)
      gc.evalFile = vcfFile
      gc.compFile = referenceVcfFile
      gc.outputFile = new File(vcfFile.getParentFile, s"$namePrefix-genotype_concordance.$name.txt")
      referenceVcfRegions.foreach(gc.intervals ::= _)
      add(gc)
      addSummarizable(gc, s"$namePrefix-genotype_concordance-$name")
    })

    for (bedFile <- ampliconBedFile.toList ::: roiBedFiles) {
      val regionName = bedFile.getName.stripSuffix(".bed")
      val vcfStats = new VcfStats(qscript)
      vcfStats.input = vcfFile
      vcfStats.intervals = Some(bedFile)
      vcfStats.setOutputDir(new File(vcfFile.getParentFile, s"vcfstats-$regionName"))
      vcfStats.infoTags :+= "VariantCaller"
      add(vcfStats)
      addSummarizable(vcfStats, s"$namePrefix-vcfstats-$name-$regionName")
    }
  }

  /** Will generate all available variantcallers */
  protected def callersList: List[Variantcaller] = List(new Freebayes(this), new RawVcf(this), new Bcftools(this), new BcftoolsSingleSample(this))

  /** Location of summary file */
  def summaryFile = new File(outputDir, "ShivaVariantcalling.summary.json")

  /** Settings for the summary */
  def summarySettings = Map(
    "variantcallers" -> configCallers.toList,
    "regions_of_interest" -> roiBedFiles.map(_.getName.stripSuffix(".bed")),
    "amplicon_bed" -> ampliconBedFile.map(_.getName.stripSuffix(".bed"))
  )

  /** Files for the summary */
  def summaryFiles: Map[String, File] = {
    val callers: Set[String] = config("variantcallers")
    callersList.filter(x => callers.contains(x.name)).map(x => x.name -> x.outputFile).toMap + ("final" -> finalFile)
  }
}