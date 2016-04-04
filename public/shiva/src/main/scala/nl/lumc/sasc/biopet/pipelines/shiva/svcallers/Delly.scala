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
package nl.lumc.sasc.biopet.pipelines.shiva.svcallers

import nl.lumc.sasc.biopet.extensions.delly.DellyCaller
import nl.lumc.sasc.biopet.extensions.gatk.CatVariants
import nl.lumc.sasc.biopet.utils.config.Configurable

/** Script for sv caller delly */
class Delly(val root: Configurable) extends SvCaller {
  def name = "delly"

  val del: Boolean = config("DEL", default = true)
  val dup: Boolean = config("DUP", default = true)
  val inv: Boolean = config("INV", default = true)
  val tra: Boolean = config("TRA", default = true)

  def biopetScript() {
    for ((sample, bamFile) <- inputBams) {
      val dellyDir = new File(outputDir, sample)

      val catVariants = new CatVariants(this)
      catVariants.outputFile = new File(dellyDir, sample + ".delly.vcf.gz")

      if (del) {
        val delly = new DellyCaller(this)
        delly.input = bamFile
        delly.analysistype = "DEL"
        delly.outputvcf = new File(dellyDir, sample + ".delly.del.vcf")
        add(delly)
        catVariants.inputFiles :+= delly.outputvcf
      }
      if (dup) {
        val delly = new DellyCaller(this)
        delly.input = bamFile
        delly.analysistype = "DUP"
        delly.outputvcf = new File(dellyDir, sample + ".delly.dup.vcf")
        add(delly)
        catVariants.inputFiles :+= delly.outputvcf
      }
      if (inv) {
        val delly = new DellyCaller(this)
        delly.input = bamFile
        delly.analysistype = "INV"
        delly.outputvcf = new File(dellyDir, sample + ".delly.inv.vcf")
        add(delly)
        catVariants.inputFiles :+= delly.outputvcf
      }
      if (tra) {
        val delly = new DellyCaller(this)
        delly.input = bamFile
        delly.analysistype = "TRA"
        delly.outputvcf = new File(dellyDir, sample + ".delly.tra.vcf")
        catVariants.inputFiles :+= delly.outputvcf
        add(delly)
      }

      require(catVariants.inputFiles.nonEmpty, "At least 1 SV-type must be selected for Delly [DEL/DUP/INV/TRA]")

      add(catVariants)
      addVCF(sample, catVariants.outputFile)
    }
  }
}
