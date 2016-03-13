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
package nl.lumc.sasc.biopet

import nl.lumc.sasc.biopet.pipelines.shiva.ShivaVariantcalling
import nl.lumc.sasc.biopet.utils.{ BiopetExecutable, MainCommand }

object BiopetExecutablePublic extends BiopetExecutable {
  def publicPipelines: List[MainCommand] = List(
    nl.lumc.sasc.biopet.pipelines.flexiprep.Flexiprep,
    nl.lumc.sasc.biopet.pipelines.mapping.Mapping,
    nl.lumc.sasc.biopet.pipelines.mapping.MultisampleMapping,
    nl.lumc.sasc.biopet.pipelines.gentrap.Gentrap,
    nl.lumc.sasc.biopet.pipelines.tinycap.TinyCap,
    nl.lumc.sasc.biopet.pipelines.bammetrics.BamMetrics,
    nl.lumc.sasc.biopet.pipelines.sage.Sage,
    nl.lumc.sasc.biopet.pipelines.bamtobigwig.Bam2Wig,
    nl.lumc.sasc.biopet.pipelines.carp.Carp,
    nl.lumc.sasc.biopet.pipelines.toucan.Toucan,
    nl.lumc.sasc.biopet.pipelines.shiva.ShivaSvCalling,
    nl.lumc.sasc.biopet.pipelines.gears.GearsSingle,
    nl.lumc.sasc.biopet.pipelines.gears.Gears
  )

  def pipelines: List[MainCommand] = List(
    nl.lumc.sasc.biopet.pipelines.shiva.Shiva,
    ShivaVariantcalling,
    nl.lumc.sasc.biopet.pipelines.basty.Basty
  ) ::: publicPipelines

  def tools: List[MainCommand] = BiopetToolsExecutable.tools
}
