package nl.lumc.sasc.biopet.core

import java.io.File

import htsjdk.samtools.reference.IndexedFastaSequenceFile
import nl.lumc.sasc.biopet.core.config.Configurable
import scala.collection.JavaConversions._

/**
 * Created by pjvan_thof on 4/6/15.
 */
trait Reference extends Configurable {

  def referenceSpecies: String = {
    root match {
      case r: Reference if r.referenceSpecies != "unknown_species" => r.referenceSpecies
      case _ => config("species", default = "unknown_species", path = super.configPath)
    }
  }

  def referenceName: String = {
    root match {
      case r: Reference if r.referenceName != "unknown_ref" => r.referenceName
      case _ => {
        val default: String = config("default", default = "unknown_ref", path = List("references", referenceSpecies))
        config("reference_name", default = default, path = super.configPath)
      }
    }
  }

  override def subPath = {
    referenceConfigPath ::: super.subPath
  }

  /** Returns the reference config path */
  def referenceConfigPath = {
    List("references", referenceSpecies, referenceName)
  }

  /** Returns the fasta file */
  def referenceFasta(): File = {
    val file: File = config("reference_fasta")
    Reference.checkFasta(file)

    val dict = new File(file.getAbsolutePath.stripSuffix(".fa").stripSuffix(".fasta") + ".dict")
    val fai = new File(file.getAbsolutePath + ".fai")

    this match {
      case c: BiopetCommandLineFunctionTrait => c.deps :::= dict :: fai :: Nil
      case _                                 =>
    }

    file
  }

  /** Create summary part for reference */
  def referenceSummary: Map[String, Any] = {
    val file = new IndexedFastaSequenceFile(referenceFasta())
    Map("contigs" ->
      (for (seq <- file.getSequenceDictionary.getSequences) yield seq.getSequenceName -> {
        val md5 = Option(seq.getAttribute("M5"))
        Map("md5" -> md5, "length" -> seq.getSequenceLength)
      }).toMap,
      "species" -> referenceSpecies,
      "name" -> referenceName
    )
  }
}

object Reference {

  /** Used as cache to avoid double checking */
  private var checked: Set[File] = Set()

  //TODO: this become obsolete when index get autogenerated

  /** Check fasta file if file exist and index file are there */
  def checkFasta(file: File): Unit = {
    if (!checked.contains(file)) {
      require(file.exists(), "Reference not found: " + file)

      val dict = new File(file.getAbsolutePath.stripSuffix(".fa").stripSuffix(".fasta") + ".dict")
      require(dict.exists(), "Reference is missing a dict file")

      val fai = new File(file.getAbsolutePath + ".fai")
      require(fai.exists(), "Reference is missing a fai file")

      require(IndexedFastaSequenceFile.canCreateIndexedFastaReader(file), "Index of reference cannot be loaded, reference: " + file)

      checked += file
    }
  }
}
