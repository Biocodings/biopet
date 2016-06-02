package nl.lumc.sasc.biopet.pipelines.gwastest.impute

import java.io.File
import java.util

import htsjdk.samtools.reference.FastaSequenceFile
import nl.lumc.sasc.biopet.core.{ BiopetQScript, PipelineCommand, Reference }
import nl.lumc.sasc.biopet.extensions.gatk.CatVariants
import nl.lumc.sasc.biopet.extensions.tools.GensToVcf
import nl.lumc.sasc.biopet.pipelines.gwastest.impute.Impute2Vcf.GensInput
import nl.lumc.sasc.biopet.utils.Logging
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.QScript

import scala.collection.JavaConversions._

/**
 * Created by pjvan_thof on 27-5-16.
 */
class Impute2Vcf(val root: Configurable) extends QScript with BiopetQScript with Reference {
  def this() = this(null)

  val specsFile: Option[File] = config("imute_specs_file")

  val phenotypeFile: File = config("phenotype_file")

  val inputGens: List[GensInput] = config("input_gens", default = Nil).asList.map {
    case value: Map[String, Any] =>
      GensInput(new File(value("genotypes").toString),
        value.get("info").map(x => new File(x.toString)),
        value("contig").toString)
    case value: util.LinkedHashMap[String, _] =>
      GensInput(new File(value.get("genotypes").toString),
        value.toMap.get("info").map(x => new File(x.toString)),
        value.get("contig").toString)
    case _ => throw new IllegalArgumentException
  } ++ (specsFile match {
    case Some(file) => Impute2Vcf.imputeSpecsToGensInput(file, config("validate_specs", default = true))
    case _          => Nil
  })

  /** Init for pipeline */
  def init(): Unit = {
    inputGens.foreach { g =>
      val referenceDict = new FastaSequenceFile(referenceFasta(), true).getSequenceDictionary
      if (referenceDict.getSequenceIndex(g.contig) == -1)
        Logging.addError(s"Contig '${g.contig}' does not exist on reference: ${referenceFasta()}")
    }
  }

  /** Pipeline itself */
  def biopetScript(): Unit = {
    require(inputGens.nonEmpty, "No vcf file or gens files defined in config")
    val cvTotal = new CatVariants(this)
    cvTotal.assumeSorted = true
    cvTotal.outputFile = new File(outputDir, "merge.gens.vcf.gz")
    val chrGens = inputGens.groupBy(_.contig).map {
      case (contig, gens) =>
        val cvChr = new CatVariants(this)
        cvChr.assumeSorted = true
        //cvChr.isIntermediate = true
        cvChr.outputFile = new File(outputDir, s"$contig.merge.gens.vcf.gz")
        gens.zipWithIndex.foreach { gen =>
          val gensToVcf = new GensToVcf(this)
          gensToVcf.inputGens = gen._1.genotypes
          gensToVcf.inputInfo = gen._1.info
          gensToVcf.contig = gen._1.contig
          gensToVcf.samplesFile = phenotypeFile
          gensToVcf.outputVcf = new File(outputDir, gen._1.genotypes.getName + s".${gen._2}.vcf.gz")
          gensToVcf.isIntermediate = true
          add(gensToVcf)
          cvChr.variant :+= gensToVcf.outputVcf
        }
        add(cvChr)
        cvTotal.variant :+= cvChr.outputFile
        contig -> cvChr.outputFile
    }
    add(cvTotal)
  }
}

object Impute2Vcf extends PipelineCommand {
  case class GensInput(genotypes: File, info: Option[File], contig: String)

  def imputeSpecsToGensInput(specsFile: File, validate: Boolean = true): List[GensInput] = {
    ImputeOutput.readSpecsFile(specsFile, validate)
      .map(x => GensInput(x.gens, Some(x.gensInfo), x.chromosome))
  }
}