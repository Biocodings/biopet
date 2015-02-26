package nl.lumc.sasc.biopet.pipelines.shiva

import java.io.File

import nl.lumc.sasc.biopet.core.{ PipelineCommand, SampleLibraryTag }
import nl.lumc.sasc.biopet.core.summary.SummaryQScript
import nl.lumc.sasc.biopet.extensions.gatk.CombineVariants
import nl.lumc.sasc.biopet.tools.{ VcfFilter, MpileupToVcf }
import nl.lumc.sasc.biopet.utils.ConfigUtils
import org.broadinstitute.gatk.utils.commandline.Input

import scala.collection.generic.Sorted

/**
 * Created by pjvan_thof on 2/26/15.
 */
trait ShivaVariantcallingTrait extends SummaryQScript with SampleLibraryTag {
  qscript =>

  @Input(doc = "Bam files (should be deduped bams)", shortName = "BAM", required = true)
  var inputBams: List[File] = Nil

  def namePrefix: String = {
    (sampleId, libId) match {
      case (Some(sampleId), Some(libId)) => sampleId + "-" + libId + "."
      case (Some(sampleId), _) => sampleId + "."
      case _ => ""
    }
  }

  def init: Unit = {
    if (!namePrefix.isEmpty && !namePrefix.endsWith(".")) namePrefix += "."
  }

  def biopetScript: Unit = {
    val cv = new CombineVariants(qscript)
    cv.outputFile = new File(outputDir, namePrefix + ".final.vcf.gz")
    for (caller <- callers) {
      caller.addJobs()
      cv.addInput(caller.outputFile, caller.name)
    }
    add(cv)

    addSummaryJobs
  }

  def callers = List(new RawVcf).filter(_.use)

  trait Variantcaller {
    val name: String
    def outputDir = new File(qscript.outputDir, name)
    protected val defaultUse: Boolean
    lazy val use: Boolean = config("use_" + name, default = defaultUse)
    protected val defaultPrio: Int
    lazy val prio: Int = config("prio_" + name, default = defaultPrio)
    def addJobs()
    def outputFile: File
  }

  class RawVcf extends Variantcaller {
    val name = "raw"
    protected val defaultPrio = 999
    protected val defaultUse = true

    def outputFile = new File(outputDir, namePrefix + "raw.vcf.gz")

    def addJobs() {
      val rawFiles = inputBams.map(bamFile => {
        val m2v = new MpileupToVcf(qscript)
        m2v.inputBam = bamFile
        m2v.output = new File(outputDir, bamFile.getName.stripSuffix(".bam") + ".raw.vcf")
        add(m2v)

        val vcfFilter = new VcfFilter(qscript) {
          override def configName = "vcffilter"
          override def defaults = ConfigUtils.mergeMaps(Map("min_sample_depth" -> 8,
            "min_alternate_depth" -> 2,
            "min_samples_pass" -> 1,
            "filter_ref_calls" -> true
          ), super.defaults)
        }
        vcfFilter.inputVcf = m2v.output
        vcfFilter.outputVcf = new File(outputDir, bamFile.getName.stripSuffix(".bam") + ".raw.filter.vcf.gz")
        add(vcfFilter)
        vcfFilter.outputVcf
      })

      val cv = new CombineVariants(qscript)
      cv.inputFiles = rawFiles
      cv.outputFile = outputFile
      add(cv)
    }
  }

  def summaryFile = new File(outputDir, "ShivaVariantcalling.summary.json")

  def summarySettings = Map()

  def summaryFiles = Map()
}