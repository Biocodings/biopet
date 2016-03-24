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
package nl.lumc.sasc.biopet.extensions.pindel

import java.io.File

import nl.lumc.sasc.biopet.core.{ Version, Reference, BiopetCommandLineFunction }
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Output, Input }

/**
 * Created by wyleung on 20-1-16.
 */
class PindelVCF(val root: Configurable) extends BiopetCommandLineFunction with Reference with Version {
  executable = config("exe", default = "pindel2vcf")

  override def defaultCoreMemory = 2.0
  override def defaultThreads = 1

  def versionRegex = """Version:?[ ]+(.*)""".r
  override def versionExitcode = List(0)
  def versionCommand = executable + " -h"

  /**
   * Required parameters
   */
  @Input
  var reference: File = referenceFasta

  @Output
  var outputVCF: File = _

  var rDate: String = config("rdate", freeVar = false)

  override def beforeGraph: Unit = {
    if (reference == null) reference = referenceFasta()
  }

  @Input(doc = "Make this file a dependency before pindel2vcf can run. Usually a file generated by Pindel such as a _D file")
  var pindelOutputInputHolder: File = _

  var pindelOutput: Option[File] = config("pindel_output")
  var pindelOutputRoot: Option[File] = config("pindel_output_root")
  var chromosome: Option[String] = config("chromosome")
  var windowSize: Option[Int] = config("window_size")
  var minCoverage: Option[Int] = config("min_coverage")
  var hetCutoff: Option[Float] = config("het_cutoff")
  var homCutoff: Option[Float] = config("hom_cutoff")
  var minSize: Option[Int] = config("min_size")
  var maxSize: Option[Int] = config("max_size")
  var bothStrandSupported: Boolean = config("both_strand_supported", default = false)
  var minSupportingSamples: Option[Int] = config("min_supporting_samples")
  var minSupportingReads: Option[Int] = config("min_supporting_reads")
  var maxSupportingReads: Option[Int] = config("max_supporting_reads")
  var regionStart: Option[Int] = config("region_start")
  var regionEnd: Option[Int] = config("region_end")
  var maxInternalRepeats: Option[Int] = config("max_internal_repeats")
  var compactOutLimit: Option[Int] = config("compact_output_limit")
  var maxInternalRepeatLength: Option[Int] = config("max_internal_repeatlength")
  var maxPostindelRepeats: Option[Int] = config("max_postindel_repeat")
  var maxPostindelRepeatLength: Option[Int] = config("max_postindel_repeatlength")
  var onlyBalancedSamples: Boolean = config("only_balanced_samples", default = false)
  var somaticP: Boolean = config("somatic_p", default = false)
  var minimumStrandSupport: Option[Int] = config("minimum_strand_support")
  var gatkCompatible: Boolean = config("gatk_compatible", default = false)

  def cmdLine = required(executable) +
    required("--reference", reference) +
    required("--reference_name", referenceSpecies) +
    required("--reference_date", rDate) +
    required("--fake_biopet_input_holder", pindelOutputInputHolder) +
    optional("--pindel_output", pindelOutput) +
    optional("--pindel_output_root", pindelOutputRoot) +
    required("--vcf", outputVCF) +
    optional("--chromosome", chromosome) +
    optional("--window_size", windowSize) +
    optional("--min_coverage", minCoverage) +
    optional("--het_cutoff", hetCutoff) +
    optional("--hom_cutoff", homCutoff) +
    optional("--min_size", minSize) +
    optional("--max_size", maxSize) +
    conditional(bothStrandSupported, "--both_strands_supported") +
    optional("--min_supporting_samples", minSupportingSamples) +
    optional("--min_supporting_reads", minSupportingReads) +
    optional("--max_supporting_reads", maxSupportingReads) +
    optional("--region_start", regionStart) +
    optional("--region_end", regionEnd) +
    optional("--max_internal_repeats", maxInternalRepeats) +
    optional("--compact_output_limit", compactOutLimit) +
    optional("--max_internal_repeatlength", maxInternalRepeatLength) +
    optional("--max_postindel_repeats", maxPostindelRepeats) +
    optional("--max_postindel_repeatlength", maxPostindelRepeatLength) +
    conditional(onlyBalancedSamples, "--only_balanced_samples") +
    conditional(somaticP, "--somatic_p") +
    optional("--minimum_strand_support", minimumStrandSupport) +
    conditional(gatkCompatible, "--gatk_compatible")
}
