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
package nl.lumc.sasc.biopet.utils

import java.io.File

import htsjdk.samtools.{ SamReader, SamReaderFactory }

import scala.collection.JavaConversions._
import scala.collection.parallel.immutable

/**
 * Created by pjvan_thof on 11/19/15.
 */
object BamUtils {

  /**
   * This method will convert a list of bam files to a Map[<sampleName>, <bamFile>]
   *
   * Each sample may only be once in the list
   *
   * @throws IllegalArgumentException
   * @param bamFiles input bam files
   * @return
   */
  def sampleBamMap(bamFiles: List[File]): Map[String, File] = {
    val temp = bamFiles.map { file =>
      val inputSam = SamReaderFactory.makeDefault.open(file)
      val samples = inputSam.getFileHeader.getReadGroups.map(_.getSample).distinct
      if (samples.size == 1) samples.head -> file
      else if (samples.size > 1) throw new IllegalArgumentException("Bam contains multiple sample IDs: " + file)
      else throw new IllegalArgumentException("Bam does not contain sample ID or have no readgroups defined: " + file)
    }
    if (temp.map(_._1).distinct.size != temp.size) throw new IllegalArgumentException("Samples has been found twice")
    temp.toMap
  }

  /**
   * Estimate the insertsize of fragments within the given contig.
   * Uses the properly paired reads according to flags set by the aligner
   *
   * @param inputBam input bam file
   * @param contig contig to scan for
   * @param end postion to stop scanning
   * @return Int with insertsize for this contig
   */
  def contigInsertSize(inputBam: File, contig: String, start: Int, end: Int, samplingSize: Int = 100000): Option[Int] = {
    val inputSam: SamReader = SamReaderFactory.makeDefault.open(inputBam)
    val samIterator = inputSam.query(contig, start, end, true)
    val insertsizes: List[Int] = (for {
      read <- samIterator.toStream.takeWhile(rec => {
        val paired = rec.getReadPairedFlag && rec.getProperPairFlag
        val bothMapped = if (paired) ((rec.getReadUnmappedFlag == false) && (rec.getMateUnmappedFlag == false)) else false
        paired && bothMapped
      }).take(samplingSize)
    } yield {
      read.getInferredInsertSize.asInstanceOf[Int].abs
    })(collection.breakOut)
    val cti = insertsizes.foldLeft((0.0, 0))((t, r) => (t._1 + r, t._2 + 1))

    samIterator.close()
    inputSam.close()
    val ret = if (cti._2 == 0) None else Some((cti._1 / cti._2).toInt)
    ret
  }

  /**
   * Estimate the insertsize for one single bamfile and return the insertsize
   *
   * @param bamFile bamfile to estimate avg insertsize from
   * @return
   */
  def sampleBamInsertSize(bamFile: File, samplingSize: Int = 100000): Int = {
    val inputSam: SamReader = SamReaderFactory.makeDefault.open(bamFile)
    val baminsertsizes = inputSam.getFileHeader.getSequenceDictionary.getSequences.par.map({
      contig => BamUtils.contigInsertSize(bamFile, contig.getSequenceName, 1, contig.getSequenceLength, samplingSize)
    }).toList
    val counts = baminsertsizes.flatMap(x => x)

    if (counts.size != 0) {
      counts.sum / counts.size
    } else {
      0
    }
  }

  /**
   * Estimate the insertsize for each bam file and return Map[<sampleBamFile>, <insertSize>]
   *
   * @param bamFiles input bam files
   * @return
   */
  def sampleBamsInsertSize(bamFiles: List[File], samplingSize: Int = 100000): immutable.ParMap[File, Int] = bamFiles.par.map { bamFile =>
    bamFile -> sampleBamInsertSize(bamFile, samplingSize)
  }.toMap

}
