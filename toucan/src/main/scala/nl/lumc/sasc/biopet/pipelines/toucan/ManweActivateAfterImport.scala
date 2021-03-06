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
package nl.lumc.sasc.biopet.pipelines.toucan

import java.io.File

import nl.lumc.sasc.biopet.extensions.manwe.{
  ManweAnnotateVcf,
  ManweSamplesActivate,
  ManweSamplesImport
}
import nl.lumc.sasc.biopet.utils.config.Configurable

import scala.io.Source

/**
  * Created by ahbbollen on 9-10-15.
  * Wrapper for manwe activate after importing and annotating
  */
class ManweActivateAfterImport(root: Configurable, imported: ManweSamplesImport)
    extends ManweSamplesActivate(root) {

  override def beforeGraph: Unit = {
    super.beforeGraph
    require(imported != null, "Imported should be defined")
    this.deps :+= imported.jobOutputFile
  }

  override def beforeCmd: Unit = {
    super.beforeCmd

    this.uri = getUri
  }

  def getUriFromFile(f: File): String = {
    val r = if (f.exists()) {
      val reader = Source.fromFile(f)
      val it = reader.getLines()
      if (it.isEmpty) {
        throw new IllegalArgumentException("Empty manwe stderr file")
      }
      it.filter(_.contains("Added sample")).toList.head.split(" ").last
    } else {
      ""
    }
    r
  }

  def getUri: String = {
    val err: Option[File] = Some(imported.jobOutputFile)
    uri = err match {
      case None => ""
      case Some(s) =>
        s match {
          case null => ""
          case other => getUriFromFile(other)
        }
      case _ => ""
    }

    uri
  }

  override def subCommand = {
    required("samples") + required("activate") + getUri
  }

}
