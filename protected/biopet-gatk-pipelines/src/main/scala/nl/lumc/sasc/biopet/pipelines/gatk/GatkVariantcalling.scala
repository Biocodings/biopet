/**
 * Due to the license issue with GATK, this part of Biopet can only be used inside the
 * LUMC. Please refer to https://git.lumc.nl/biopet/biopet/wikis/home for instructions
 * on how to use this protected part of biopet or contact us at sasc@lumc.nl
 */
package nl.lumc.sasc.biopet.pipelines.gatk

import nl.lumc.sasc.biopet.core.{ BiopetQScript, PipelineCommand }
import java.io.File
import nl.lumc.sasc.biopet.tools.{ VcfStats, MpileupToVcf, VcfFilter, MergeAlleles }
import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.extensions.gatk.{ AnalyzeCovariates, BaseRecalibrator, GenotypeGVCFs, HaplotypeCaller, IndelRealigner, PrintReads, RealignerTargetCreator, SelectVariants, CombineVariants, UnifiedGenotyper }
import nl.lumc.sasc.biopet.extensions.picard.MarkDuplicates
import nl.lumc.sasc.biopet.utils.ConfigUtils
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk.TaggedFile
import org.broadinstitute.gatk.utils.commandline.{ Input, Argument }
import scala.collection.SortedMap
import scala.language.reflectiveCalls

class GatkVariantcalling(val root: Configurable) extends QScript with BiopetQScript {
  def this() = this(null)

  val scriptOutput = new GatkVariantcalling.ScriptOutput

  @Input(doc = "Bam files (should be deduped bams)", shortName = "BAM")
  var inputBams: List[File] = Nil

  @Input(doc = "Raw vcf file", shortName = "raw")
  var rawVcfInput: File = _

  @Argument(doc = "Reference", shortName = "R", required = false)
  var reference: File = config("reference", required = true)

  @Argument(doc = "OutputName", required = false)
  var outputName: String = _

  @Argument(doc = "Sample name", required = false)
  var sampleID: String = _

  var preProcesBams: Option[Boolean] = config("pre_proces_bams", default = true)
  var variantcalling: Boolean = true
  var doublePreProces: Option[Boolean] = config("double_pre_proces", default = true)
  var useHaplotypecaller: Option[Boolean] = config("use_haplotypecaller", default = true)
  var useUnifiedGenotyper: Option[Boolean] = config("use_unifiedgenotyper", default = false)
  var useAllelesOption: Option[Boolean] = config("use_alleles_option", default = false)
  var useMpileup: Boolean = config("use_mpileup", default = true)
  var useIndelRealigner: Boolean = config("use_indel_realign", default = true)
  var useBaseRecalibration: Boolean = config("use_base_recalibration", default = true)

  def init() {
    if (outputName == null && sampleID != null) outputName = sampleID
    else if (outputName == null) outputName = "noname"
    if (outputDir == null) throw new IllegalStateException("Missing Output directory on gatk module")
    else if (!outputDir.endsWith("/")) outputDir += "/"

    val baseRecalibrator = new BaseRecalibrator(this)
    if (preProcesBams && useBaseRecalibration && baseRecalibrator.knownSites.isEmpty) {
      logger.warn("No Known site found, skipping base recalibration")
      useBaseRecalibration = false
    }
  }

  private def doublePreProces(files: List[File]): List[File] = {
    if (files.size == 1) return files
    if (files.isEmpty) throw new IllegalStateException("Files can't be empty")
    if (!doublePreProces.get) return files
    val markDup = MarkDuplicates(this, files, new File(outputDir + outputName + ".dedup.bam"))
    markDup.isIntermediate = useIndelRealigner
    add(markDup)
    if (useIndelRealigner) {
      List(addIndelRealign(markDup.output, outputDir, isIntermediate = false))
    } else {
      List(markDup.output)
    }
  }

  def biopetScript() {
    scriptOutput.bamFiles = if (preProcesBams.get) {
      var bamFiles: List[File] = Nil
      for (inputBam <- inputBams) {
        var bamFile = inputBam
        if (useIndelRealigner) {
          bamFile = addIndelRealign(bamFile, outputDir, isIntermediate = useBaseRecalibration)
        }
        if (useBaseRecalibration) {
          bamFile = addBaseRecalibrator(bamFile, outputDir, isIntermediate = bamFiles.size > 1)
        }
        bamFiles :+= bamFile
      }
      doublePreProces(bamFiles)
    } else if (inputBams.size > 1 && doublePreProces.get) {
      doublePreProces(inputBams)
    } else inputBams

    if (variantcalling) {
      var mergBuffer: SortedMap[String, File] = SortedMap()
      def mergeList = mergBuffer map { case (key, file) => TaggedFile(removeNoneVariants(file), "name=" + key) }

      if (sampleID != null && (useHaplotypecaller.get || config("joint_genotyping", default = false).asBoolean)) {
        val hcGvcf = new HaplotypeCaller(this)
        hcGvcf.useGvcf
        hcGvcf.input_file = scriptOutput.bamFiles
        hcGvcf.out = outputDir + outputName + ".hc.discovery.gvcf.vcf.gz"
        add(hcGvcf)
        scriptOutput.gvcfFile = hcGvcf.out
      }

      if (useHaplotypecaller.get) {
        if (sampleID != null) {
          val genotypeGVCFs = GenotypeGVCFs(this, List(scriptOutput.gvcfFile), outputDir + outputName + ".hc.discovery.vcf.gz")
          add(genotypeGVCFs)
          scriptOutput.hcVcfFile = genotypeGVCFs.out
        } else {
          val hcGvcf = new HaplotypeCaller(this)
          hcGvcf.input_file = scriptOutput.bamFiles
          hcGvcf.out = outputDir + outputName + ".hc.discovery.vcf.gz"
          add(hcGvcf)
          scriptOutput.hcVcfFile = hcGvcf.out
        }
        mergBuffer += ("1.HC-Discovery" -> scriptOutput.hcVcfFile)
      }

      if (useUnifiedGenotyper.get) {
        val ugVcf = new UnifiedGenotyper(this)
        ugVcf.input_file = scriptOutput.bamFiles
        ugVcf.out = outputDir + outputName + ".ug.discovery.vcf.gz"
        add(ugVcf)
        scriptOutput.ugVcfFile = ugVcf.out
        mergBuffer += ("2.UG-Discovery" -> scriptOutput.ugVcfFile)
      }

      // Generate raw vcf
      if (useMpileup) {
        if (sampleID != null && scriptOutput.bamFiles.size == 1) {
          val m2v = new MpileupToVcf(this)
          m2v.inputBam = scriptOutput.bamFiles.head
          m2v.sample = sampleID
          m2v.output = outputDir + outputName + ".raw.vcf"
          add(m2v)
          scriptOutput.rawVcfFile = m2v.output

          val vcfFilter = new VcfFilter(this) {
            override def defaults = ConfigUtils.mergeMaps(Map("min_sample_depth" -> 8,
              "min_alternate_depth" -> 2,
              "min_samples_pass" -> 1,
              "filter_ref_calls" -> true
            ), super.defaults)
          }
          vcfFilter.inputVcf = m2v.output
          vcfFilter.outputVcf = this.swapExt(outputDir, m2v.output, ".vcf", ".filter.vcf.gz")
          add(vcfFilter)
          scriptOutput.rawFilterVcfFile = vcfFilter.outputVcf
        } else if (rawVcfInput != null) scriptOutput.rawFilterVcfFile = rawVcfInput
        if (scriptOutput.rawFilterVcfFile != null) mergBuffer += ("9.raw" -> scriptOutput.rawFilterVcfFile)
      }

      // Allele mode
      if (useAllelesOption.get) {
        val mergeAlleles = MergeAlleles(this, mergeList.toList, outputDir + "raw.allele__temp_only.vcf.gz")
        mergeAlleles.isIntermediate = true
        add(mergeAlleles)

        if (useHaplotypecaller.get) {
          val hcAlleles = new HaplotypeCaller(this)
          hcAlleles.input_file = scriptOutput.bamFiles
          hcAlleles.out = outputDir + outputName + ".hc.allele.vcf.gz"
          hcAlleles.alleles = mergeAlleles.output
          hcAlleles.genotyping_mode = org.broadinstitute.gatk.tools.walkers.genotyper.GenotypingOutputMode.GENOTYPE_GIVEN_ALLELES
          add(hcAlleles)
          scriptOutput.hcAlleleVcf = hcAlleles.out
          mergBuffer += ("3.HC-alleles" -> hcAlleles.out)
        }

        if (useUnifiedGenotyper.get) {
          val ugAlleles = new UnifiedGenotyper(this)
          ugAlleles.input_file = scriptOutput.bamFiles
          ugAlleles.out = outputDir + outputName + ".ug.allele.vcf.gz"
          ugAlleles.alleles = mergeAlleles.output
          ugAlleles.genotyping_mode = org.broadinstitute.gatk.tools.walkers.genotyper.GenotypingOutputMode.GENOTYPE_GIVEN_ALLELES
          add(ugAlleles)
          scriptOutput.ugAlleleVcf = ugAlleles.out
          mergBuffer += ("4.UG-alleles" -> ugAlleles.out)
        }
      }

      def removeNoneVariants(input: File): File = {
        val output = input.getAbsolutePath.stripSuffix(".vcf.gz") + ".variants_only.vcf.gz"
        val sv = SelectVariants(this, input, output)
        sv.excludeFiltered = true
        sv.excludeNonVariants = true
        sv.isIntermediate = true
        add(sv)
        sv.out
      }

      val cvFinal = CombineVariants(this, mergeList.toList, outputDir + outputName + ".final.vcf.gz")
      cvFinal.genotypemergeoption = org.broadinstitute.gatk.utils.variant.GATKVariantContextUtils.GenotypeMergeType.UNSORTED
      add(cvFinal)

      val vcfStats = new VcfStats(this)
      vcfStats.input = cvFinal.out
      vcfStats.setOutputDir(outputDir + File.separator + "vcfstats")
      add(vcfStats)

      scriptOutput.finalVcfFile = cvFinal.out
    }
  }

  def addIndelRealign(inputBam: File, dir: String, isIntermediate: Boolean = true): File = {
    val realignerTargetCreator = RealignerTargetCreator(this, inputBam, dir)
    realignerTargetCreator.isIntermediate = true
    add(realignerTargetCreator)

    val indelRealigner = IndelRealigner.apply(this, inputBam, realignerTargetCreator.out, dir)
    indelRealigner.isIntermediate = isIntermediate
    add(indelRealigner)

    return indelRealigner.o
  }

  def addBaseRecalibrator(inputBam: File, dir: String, isIntermediate: Boolean = false): File = {
    val baseRecalibrator = BaseRecalibrator(this, inputBam, swapExt(dir, inputBam, ".bam", ".baserecal"))

    if (baseRecalibrator.knownSites.isEmpty) {
      logger.warn("No Known site found, skipping base recalibration, file: " + inputBam)
      return inputBam
    }
    add(baseRecalibrator)

    if (config("use_analyze_covariates", default = false).asBoolean) {
      val baseRecalibratorAfter = BaseRecalibrator(this, inputBam, swapExt(dir, inputBam, ".bam", ".baserecal.after"))
      baseRecalibratorAfter.BQSR = baseRecalibrator.o
      add(baseRecalibratorAfter)

      add(AnalyzeCovariates(this, baseRecalibrator.o, baseRecalibratorAfter.o, swapExt(dir, inputBam, ".bam", ".baserecal.pdf")))
    }

    val printReads = PrintReads(this, inputBam, swapExt(dir, inputBam, ".bam", ".baserecal.bam"))
    printReads.BQSR = baseRecalibrator.o
    printReads.isIntermediate = isIntermediate
    add(printReads)

    return printReads.o
  }
}

object GatkVariantcalling extends PipelineCommand {
  class ScriptOutput {
    var bamFiles: List[File] = _
    var gvcfFile: File = _
    var hcVcfFile: File = _
    var ugVcfFile: File = _
    var rawVcfFile: File = _
    var rawFilterVcfFile: File = _
    var hcAlleleVcf: File = _
    var ugAlleleVcf: File = _
    var finalVcfFile: File = _
  }
}
