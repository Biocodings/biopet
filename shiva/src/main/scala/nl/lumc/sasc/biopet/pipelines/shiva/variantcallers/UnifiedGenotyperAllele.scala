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
 * A dual licensing mode is applied. The source code within this project is freely available for non-commercial use under an AGPL
 * license; For commercial users or users who do not want to follow the AGPL
 * license, please contact us to obtain a separate license.
 */
/**
 * Due to the license issue with GATK, this part of Biopet can only be used inside the
 * LUMC. Please refer to https://git.lumc.nl/biopet/biopet/wikis/home for instructions
 * on how to use this protected part of biopet or contact us at sasc@lumc.nl
 */
package nl.lumc.sasc.biopet.pipelines.shiva.variantcallers

import nl.lumc.sasc.biopet.extensions.gatk
import nl.lumc.sasc.biopet.utils.config.Configurable

/** Allele mode for GenotyperAllele */
class UnifiedGenotyperAllele(val root: Configurable) extends Variantcaller {
  val name = "unifiedgenotyper_allele"
  protected def defaultPrio = 9

  def biopetScript() {
    val ug = gatk.UnifiedGenotyper(this, inputBams.values.toList, outputFile)
    ug.alleles = config("input_alleles")
    ug.genotyping_mode = Some("GENOTYPE_GIVEN_ALLELES")
    add(ug)
  }
}
