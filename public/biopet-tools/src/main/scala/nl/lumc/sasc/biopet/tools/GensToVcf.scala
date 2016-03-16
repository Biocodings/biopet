package nl.lumc.sasc.biopet.tools

import java.io.File
import java.util

import htsjdk.samtools.reference.FastaSequenceFile
import htsjdk.variant.variantcontext.{ GenotypeBuilder, VariantContextBuilder}
import htsjdk.variant.variantcontext.writer.{ VariantContextWriterBuilder, AsyncVariantContextWriter }
import htsjdk.variant.vcf._
import nl.lumc.sasc.biopet.utils.ToolCommand

import scala.collection.JavaConversions._

import scala.io.Source

/**
  * Created by pjvanthof on 15/03/16.
  */
object GensToVcf extends ToolCommand {

  case class Args(inputGenotypes: File = null,
                  inputInfo: Option[File] = None,
                  outputVcf: File = null,
                  sampleFile: File = null,
                  referenceFasta: File = null,
                  contig: String = null) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('g', "inputGenotypes") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(inputGenotypes = x)
    } text "Input genotypes"
    opt[File]('i', "inputInfo") maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(inputInfo = Some(x))
    } text "Input info fields"
    opt[File]('o', "outputVcf") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(outputVcf = x)
    } text "Output vcf file"
    opt[File]('s', "samplesFile") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(sampleFile = x)
    } text "Samples file"
    opt[File]('R', "referenceFasta") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(referenceFasta = x)
    } text "reference fasta file"
    opt[String]('c', "contig") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(contig = x)
    } text "contig of impute file"
  }

  def main(args: Array[String]): Unit = {
    val argsParser = new OptParser
    val cmdArgs = argsParser.parse(args, Args()).getOrElse(throw new IllegalArgumentException)

    val samples = Source.fromFile(cmdArgs.sampleFile).getLines().toArray.drop(2).map(_.split("\t").head)

    val metaLines = new util.HashSet[VCFHeaderLine]()
    metaLines.add(new VCFFormatHeaderLine("GP", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, ""))

    val reference = new FastaSequenceFile(cmdArgs.referenceFasta, true)
    require(reference.getSequenceDictionary.getSequence(cmdArgs.contig) != null,
      s"contig '${cmdArgs.contig}' not found on reference")

    val header = new VCFHeader(metaLines, samples.toList)
    header.setSequenceDictionary(reference.getSequenceDictionary)
    val writer = new AsyncVariantContextWriter(new VariantContextWriterBuilder()
      .setOutputFile(cmdArgs.outputVcf)
      .setReferenceDictionary(header.getSequenceDictionary)
      .build)
    writer.writeHeader(header)

    val genotypeIt = Source.fromFile(cmdArgs.inputGenotypes).getLines()
    //TODO: Add info fields
    val infoIt = cmdArgs.inputInfo.map(Source.fromFile(_).getLines())

    for (genotypeLine <- genotypeIt) {
      val genotypeValues = genotypeLine.split(" ")
      val start = genotypeValues(2).toInt
      val end = genotypeValues(3).length - 1 + start
      val genotypes = samples.toList.zipWithIndex.map { case (sampleName, index) =>
        new GenotypeBuilder()
          .name(sampleName)
          .attribute("GP", Array(genotypeValues(5 + (index * 3)), genotypeValues(5 + (index * 3) + 1), genotypeValues(5 + (index * 3) + 2)).map(_.toDouble))
          .make()
      }

      val builder = (new VariantContextBuilder)
        .chr(cmdArgs.contig)
        .alleles(genotypeValues(3), genotypeValues(4))
        .start(start)
        .stop(end)
        .genotypes(genotypes)
      val id = genotypeValues(1)
      if (id.startsWith(cmdArgs.contig + ":")) writer.add(builder.make())
      else writer.add(builder.id(id).make())
    }

    writer.close()
  }
}
