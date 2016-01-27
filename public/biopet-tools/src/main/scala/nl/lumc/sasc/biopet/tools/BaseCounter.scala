package nl.lumc.sasc.biopet.tools

import java.io.{ PrintWriter, File }

import htsjdk.samtools.{ SAMRecord, SamReaderFactory }
import nl.lumc.sasc.biopet.utils.ToolCommand
import nl.lumc.sasc.biopet.utils.intervals.{ BedRecordList, BedRecord }
import picard.annotation.{ Gene, GeneAnnotationReader }

import scala.collection.JavaConversions._

/**
 * Created by pjvanthof on 22/01/16.
 */
object BaseCounter extends ToolCommand {

  case class Args(refFlat: File = null,
                  outputDir: File = null,
                  bamFile: File = null,
                  prefix: String = "output") extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('r', "refFlat") required () valueName "<file>" action { (x, c) =>
      c.copy(refFlat = x)
    }
    opt[File]('o', "outputDir") required () valueName "<directory>" action { (x, c) =>
      c.copy(outputDir = x)
    }
    opt[File]('b', "bam") required () valueName "<file>" action { (x, c) =>
      c.copy(bamFile = x)
    }
    opt[String]('p', "prefix") valueName "<prefix>" action { (x, c) =>
      c.copy(prefix = x)
    }
  }

  def main(args: Array[String]): Unit = {
    val argsParser = new OptParser
    val cmdArgs: Args = argsParser.parse(args, Args()) match {
      case Some(x) => x
      case _       => throw new IllegalArgumentException
    }

    //Sets picard logging level
    htsjdk.samtools.util.Log.setGlobalLogLevel(htsjdk.samtools.util.Log.LogLevel.valueOf(logger.getLevel.toString))

    logger.info("Start reading RefFlat file")
    val bamReader = SamReaderFactory.makeDefault().open(cmdArgs.bamFile)
    val geneReader = GeneAnnotationReader.loadRefFlat(cmdArgs.refFlat, bamReader.getFileHeader.getSequenceDictionary)
    bamReader.close()
    logger.info("Done reading RefFlat file")

    logger.info("Finding overlapping genes")
    val overlapGenes = groupGenesOnOverlap(geneReader.getAll)

    logger.info("Start reading bamFile")
    val counts = (for (genes <- overlapGenes.values.flatten.par) yield bamToGeneCount(cmdArgs.bamFile, genes)).toList.flatten
    logger.info("Done reading bamFile")

    writeGeneCounts(counts, cmdArgs.outputDir, cmdArgs.prefix)
    writeMergeExonCount(counts, cmdArgs.outputDir, cmdArgs.prefix)
    writeMergeIntronCount(counts, cmdArgs.outputDir, cmdArgs.prefix)
    writeTranscriptCounts(counts, cmdArgs.outputDir, cmdArgs.prefix)
    writeExonCount(counts, cmdArgs.outputDir, cmdArgs.prefix)
    writeIntronCount(counts, cmdArgs.outputDir, cmdArgs.prefix)
  }

  /**
   * This function will write all counts that are concatenated on transcript level. Each line is 1 transcript.
   * Exonic: then it's seen as an exon on 1 of the transcripts
   * Intronic: then it's not seen as an exon on 1 of the transcripts
   * Exonic + Intronic = Total
   */
  def writeTranscriptCounts(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val transcriptTotalWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.counts"))
    val transcriptTotalSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.sense.counts"))
    val transcriptTotalAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.antisense.counts"))
    val transcriptExonicWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.exonic.counts"))
    val transcriptExonicSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.exonic.sense.counts"))
    val transcriptExonicAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.exonic.antisense.counts"))
    val transcriptIntronicWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.intronic.counts"))
    val transcriptIntronicSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.intronic.sense.counts"))
    val transcriptIntronicAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.transcript.intronic.antisense.counts"))

    genes.flatMap(_.transcripts).sortBy(_.transcript.name).foreach { transcriptCount =>
      transcriptTotalWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.counts.totalBases)
      transcriptTotalSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.counts.senseBases)
      transcriptTotalAntiSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.counts.antiSenseBases)
      transcriptExonicWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.exonCounts.map(_.counts.totalBases).sum)
      transcriptExonicSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.exonCounts.map(_.counts.senseBases).sum)
      transcriptExonicAntiSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.exonCounts.map(_.counts.antiSenseBases).sum)
      transcriptIntronicWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.intronCounts.map(_.counts.totalBases).sum)
      transcriptIntronicSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.intronCounts.map(_.counts.senseBases).sum)
      transcriptIntronicAntiSenseWriter.println(transcriptCount.transcript.name + "\t" + transcriptCount.intronCounts.map(_.counts.antiSenseBases).sum)
    }

    transcriptTotalWriter.close()
    transcriptTotalSenseWriter.close()
    transcriptTotalAntiSenseWriter.close()
    transcriptExonicWriter.close()
    transcriptExonicSenseWriter.close()
    transcriptExonicAntiSenseWriter.close()
    transcriptIntronicWriter.close()
    transcriptIntronicSenseWriter.close()
    transcriptIntronicAntiSenseWriter.close()
  }

  /** This will write counts on each exon */
  def writeExonCount(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val exonWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.counts"))
    val exonSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.sense.counts"))
    val exonAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.antisense.counts"))

    genes.flatMap(_.transcripts).sortBy(_.transcript.name).foreach { transcriptCount =>
      transcriptCount.exonCounts.foreach { exonCount =>
        exonWriter.println(transcriptCount.transcript.name + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.totalBases)
        exonSenseWriter.println(transcriptCount.transcript.name + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.senseBases)
        exonAntiSenseWriter.println(transcriptCount.transcript.name + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.antiSenseBases)
      }
    }

    exonWriter.close()
    exonSenseWriter.close()
    exonAntiSenseWriter.close()
  }

  /** This will write counts on each intron */
  def writeIntronCount(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val intronWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.counts"))
    val intronSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.sense.counts"))
    val intronAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.antisense.counts"))

    genes.flatMap(_.transcripts).sortBy(_.transcript.name).foreach { transcriptCount =>
      transcriptCount.intronCounts.foreach { intronCount =>
        intronWriter.println(transcriptCount.transcript.name + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.totalBases)
        intronSenseWriter.println(transcriptCount.transcript.name + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.senseBases)
        intronAntiSenseWriter.println(transcriptCount.transcript.name + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.antiSenseBases)
      }
    }

    intronWriter.close()
    intronSenseWriter.close()
    intronAntiSenseWriter.close()
  }

  /**
   * This function will write all counts that are concatenated on gene level. Each line is 1 gene.
   * Exonic: then it's seen as an exon on 1 of the transcripts
   * Intronic: then it's not seen as an exon on 1 of the transcripts
   * Exonic + Intronic = Total
   */
  def writeGeneCounts(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val geneTotalWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.counts"))
    val geneTotalSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.sense.counts"))
    val geneTotalAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.antisense.counts"))
    val geneExonicWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.exonic.counts"))
    val geneExonicSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.exonic.sense.counts"))
    val geneExonicAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.exonic.antisense.counts"))
    val geneIntronicWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.intronic.counts"))
    val geneIntronicSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.intronic.sense.counts"))
    val geneIntronicAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.gene.intronic.antisense.counts"))

    genes.sortBy(_.gene.getName).foreach { geneCount =>
      geneTotalWriter.println(geneCount.gene.getName + "\t" + geneCount.counts.totalBases)
      geneTotalSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.counts.senseBases)
      geneTotalAntiSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.counts.antiSenseBases)
      geneExonicWriter.println(geneCount.gene.getName + "\t" + geneCount.exonCounts.map(_.counts.totalBases).sum)
      geneExonicSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.exonCounts.map(_.counts.senseBases).sum)
      geneExonicAntiSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.exonCounts.map(_.counts.antiSenseBases).sum)
      geneIntronicWriter.println(geneCount.gene.getName + "\t" + geneCount.intronCounts.map(_.counts.totalBases).sum)
      geneIntronicSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.intronCounts.map(_.counts.senseBases).sum)
      geneIntronicAntiSenseWriter.println(geneCount.gene.getName + "\t" + geneCount.intronCounts.map(_.counts.antiSenseBases).sum)
    }

    geneTotalWriter.close()
    geneTotalSenseWriter.close()
    geneTotalAntiSenseWriter.close()
    geneExonicWriter.close()
    geneExonicSenseWriter.close()
    geneExonicAntiSenseWriter.close()
    geneIntronicWriter.close()
    geneIntronicSenseWriter.close()
    geneIntronicAntiSenseWriter.close()
  }

  /**
   * This function will print all counts that exist on exonic regions,
   * each base withing the gene is only represented once but all regions are separated
   */
  def writeMergeExonCount(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val exonWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.merge.counts"))
    val exonSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.merge.sense.counts"))
    val exonAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.exon.merge.antisense.counts"))

    genes.sortBy(_.gene.getName).foreach { geneCount =>
      geneCount.exonCounts.foreach { exonCount =>
        exonWriter.println(geneCount.gene.getName + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.totalBases)
        exonSenseWriter.println(geneCount.gene.getName + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.senseBases)
        exonAntiSenseWriter.println(geneCount.gene.getName + s"_exon:${exonCount.start}-${exonCount.end}\t" + exonCount.counts.antiSenseBases)
      }
    }

    exonWriter.close()
    exonSenseWriter.close()
    exonAntiSenseWriter.close()
  }

  /**
   * This function will print all counts that does *not* exist on exonic regions,
   * each base withing the gene is only represented once but all regions are separated
   */
  def writeMergeIntronCount(genes: List[GeneCount], outputDir: File, prefix: String): Unit = {
    val intronWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.merge.counts"))
    val intronSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.merge.sense.counts"))
    val intronAntiSenseWriter = new PrintWriter(new File(outputDir, s"$prefix.base.intron.merge.antisense.counts"))

    genes.sortBy(_.gene.getName).foreach { geneCount =>
      geneCount.intronCounts.foreach { intronCount =>
        intronWriter.println(geneCount.gene.getName + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.totalBases)
        intronSenseWriter.println(geneCount.gene.getName + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.senseBases)
        intronAntiSenseWriter.println(geneCount.gene.getName + s"_intron:${intronCount.start}-${intronCount.end}\t" + intronCount.counts.antiSenseBases)
      }
    }

    intronWriter.close()
    intronSenseWriter.close()
    intronAntiSenseWriter.close()
  }

  def samRecordStrand(samRecord: SAMRecord, gene: Gene): Boolean = {
    if (samRecord.getReadPairedFlag && samRecord.getSecondOfPairFlag)
      samRecord.getReadNegativeStrandFlag != gene.isPositiveStrand
    else samRecord.getReadNegativeStrandFlag == gene.isPositiveStrand
  }

  def bamToGeneCount(bamFile: File, genes: List[Gene]): List[GeneCount] = {
    val counts = genes.map(gene => gene -> new GeneCount(gene)).toMap
    val bamReader = SamReaderFactory.makeDefault().open(bamFile)

    val start = genes.map(_.getStart).min
    val end = genes.map(_.getEnd).max

    for (record <- bamReader.queryOverlapping(genes.head.getContig, start, end) if !record.getNotPrimaryAlignmentFlag) {
      counts.foreach { case (gene, count) => count.addRecord(record, samRecordStrand(record, gene)) }
    }

    bamReader.close()
    counts.values.toList
  }

  def createMetaExonCounts(genes: List[Gene]): List[(String, RegionCount)] = {
    val regions = genes.map(gene => gene.getName -> generateMergedExonRegions(gene).sorted)
    val chr = genes.head.getContig
    val begin = regions.map(_._2.allRecords.head.start).min
    val end = regions.map(_._2.allRecords.last.end).max

    val posibleEnds = (regions.flatMap(_._2.allRecords.map(_.end)) ++ regions.flatMap(_._2.allRecords.map(_.start))).distinct.sorted

    def mergeRegions(newBegin: Int, output: List[(String, RegionCount)] = Nil): List[(String, RegionCount)] = {
      if (newBegin > end) output
      else {
        val newEnd = posibleEnds.filter(_ > begin).min
        val record = BedRecord(chr, newBegin, newEnd)
        val names = regions.filter(_._2.overlapWith(record).size > 0).map(_._1)
        if (names.nonEmpty) mergeRegions(newEnd, (names.mkString(","), new RegionCount(record.start + 1, record.end)) :: output)
        else mergeRegions(newEnd, output)
      }
    }
    mergeRegions(begin)
  }

  def bamRecordBasesOverlap(samRecord: SAMRecord, start: Int, end: Int): Int = {
    samRecord.getAlignmentBlocks
      .map { block =>
        val blockStart = block.getReferenceStart
        val blockEnd = blockStart + block.getLength - 1
        if (start <= blockEnd && end >= blockStart) {
          (if (end < blockEnd) end else blockEnd) - (if (start > blockStart) start else blockStart)
        } else 0
      }.sum
  }

  def bamRecordBasesOverlap(samRecord: SAMRecord, start: Int, end: Int, counts: Counts, sense: Boolean): Int = {
    val overlap = bamRecordBasesOverlap(samRecord, start, end)
    if (overlap > 0) {
      if (sense) {
        counts.senseBases += overlap
        counts.senseReads += 1
      } else {
        counts.antiSenseBases += overlap
        counts.antiSenseReads += 1
      }
    }
    overlap
  }

  def groupGenesOnOverlap(genes: Iterable[Gene]) = {
    genes.groupBy(_.getContig)
      .map { case (contig, genes) => contig -> genes.toList
        .sortBy(_.getStart).foldLeft(List[List[Gene]]()) { (list, gene) =>
        if (list.isEmpty) List(List(gene))
        else if (list.head.exists(_.getEnd >= gene.getStart)) (gene :: list.head) :: list.tail
        else List(gene) :: list
      }
      }
  }

  class Counts {
    var senseBases = 0L
    var antiSenseBases = 0L
    def totalBases = senseBases + antiSenseBases
    var senseReads = 0L
    var antiSenseReads = 0L
    def totalReads = senseReads + antiSenseReads
  }

  def generateMergedExonRegions(gene: Gene) = {
    BedRecordList.fromList(gene.iterator()
      .flatMap(_.exons)
      .map(e => BedRecord(gene.getContig, e.start - 1, e.end, Some(gene.getName))))
      .combineOverlap
  }

  class GeneCount(val gene: Gene) {
    val counts = new Counts
    val transcripts = gene.iterator().map(new TranscriptCount(_)).toList
    def intronRegions = BedRecordList.fromList(BedRecord(gene.getContig, gene.getStart - 1, gene.getEnd) :: generateMergedExonRegions(gene).allRecords.toList)
      .squishBed(false, false)

    val exonCounts = generateMergedExonRegions(gene).allRecords.map(e => new RegionCount(e.start + 1, e.end))
    val intronCounts = intronRegions.allRecords.map(e => new RegionCount(e.start + 1, e.end))

    def addRecord(samRecord: SAMRecord, sense: Boolean): Unit = {
      bamRecordBasesOverlap(samRecord, gene.getStart, gene.getEnd, counts, sense)
      transcripts.foreach(_.addRecord(samRecord, sense))
      exonCounts.foreach(_.addRecord(samRecord, sense))
      intronCounts.foreach(_.addRecord(samRecord, sense))
    }
  }

  class TranscriptCount(val transcript: Gene#Transcript) {
    val counts = new Counts
    def intronRegions = BedRecordList.fromList(BedRecord(transcript.getGene.getContig, transcript.start() - 1, transcript.end()) ::
      transcript.exons.map(e => BedRecord(transcript.getGene.getContig, e.start - 1, e.end)).toList)
      .squishBed(false, false)

    val exonCounts = transcript.exons.map(new RegionCount(_))
    val intronCounts = if (transcript.exons.size > 1)
      intronRegions.allRecords.map(e => new RegionCount(e.start + 1, e.end)).toList
    else Nil
    def addRecord(samRecord: SAMRecord, sense: Boolean): Unit = {
      bamRecordBasesOverlap(samRecord, transcript.start, transcript.end, counts, sense)
      exonCounts.foreach(_.addRecord(samRecord, sense))
      intronCounts.foreach(_.addRecord(samRecord, sense))
    }
  }

  class RegionCount(val start: Int, val end: Int) {
    def this(exon: Gene#Transcript#Exon) = this(exon.start, exon.end)
    val counts = new Counts
    def addRecord(samRecord: SAMRecord, sense: Boolean): Unit = {
      bamRecordBasesOverlap(samRecord, start, end, counts, sense)
    }
  }
}