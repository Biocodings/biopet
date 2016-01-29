package nl.lumc.sasc.biopet.tools

import htsjdk.samtools.{SAMReadGroupRecord, SAMSequenceRecord, SAMLineParser, SAMFileHeader}
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test
import picard.annotation.Gene

import scala.collection.JavaConversions._

/**
  * Created by pjvan_thof on 1/29/16.
  */
class BaseCounterTest extends TestNGSuite with Matchers {

  import BaseCounter._
  import BaseCounterTest._

  @Test
  def testCountsClass(): Unit = {
    val counts = new Counts
    counts.antiSenseBases shouldBe 0
    counts.antiSenseReads shouldBe 0
    counts.senseBases shouldBe 0
    counts.senseReads shouldBe 0
    counts.totalBases shouldBe 0
    counts.totalReads shouldBe 0

    counts.antiSenseBases = 1
    counts.senseBases = 2
    counts.totalBases shouldBe 3

    counts.antiSenseReads = 1
    counts.senseReads = 2
    counts.totalReads shouldBe 3
  }

  @Test
  def testBamRecordBasesOverlapBlocks(): Unit = {
    val read = BaseCounterTest.lineParser.parseLine("r02\t0\tchrQ\t50\t60\t4M2D4M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    bamRecordBasesOverlap(read, 40, 70) shouldBe 8
    bamRecordBasesOverlap(read, 50, 59) shouldBe 8
    bamRecordBasesOverlap(read, 50, 55) shouldBe 4
    bamRecordBasesOverlap(read, 55, 60) shouldBe 4
    bamRecordBasesOverlap(read, 10, 20) shouldBe 0
    bamRecordBasesOverlap(read, 40, 49) shouldBe 0
    bamRecordBasesOverlap(read, 40, 50) shouldBe 1
    bamRecordBasesOverlap(read, 40, 51) shouldBe 2
    bamRecordBasesOverlap(read, 58, 70) shouldBe 2
    bamRecordBasesOverlap(read, 59, 70) shouldBe 1
    bamRecordBasesOverlap(read, 60, 70) shouldBe 0
  }

    @Test
  def testBamRecordBasesOverlap(): Unit = {
    val read = BaseCounterTest.lineParser.parseLine("r02\t0\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    bamRecordBasesOverlap(read, 40, 70) shouldBe 10
    bamRecordBasesOverlap(read, 50, 59) shouldBe 10
    bamRecordBasesOverlap(read, 50, 55) shouldBe 6
    bamRecordBasesOverlap(read, 55, 60) shouldBe 5
    bamRecordBasesOverlap(read, 10, 20) shouldBe 0
    bamRecordBasesOverlap(read, 40, 49) shouldBe 0
    bamRecordBasesOverlap(read, 40, 50) shouldBe 1
    bamRecordBasesOverlap(read, 40, 51) shouldBe 2
    bamRecordBasesOverlap(read, 58, 70) shouldBe 2
    bamRecordBasesOverlap(read, 59, 70) shouldBe 1
    bamRecordBasesOverlap(read, 60, 70) shouldBe 0

    val counts = new Counts
    bamRecordBasesOverlap(read, 40, 70, counts, true)
    counts.senseBases shouldBe 10
    counts.antiSenseBases shouldBe 0
    counts.senseReads shouldBe 1
    counts.antiSenseReads shouldBe 0

    bamRecordBasesOverlap(read, 50, 54, counts, false)
    counts.senseBases shouldBe 10
    counts.antiSenseBases shouldBe 5
    counts.senseReads shouldBe 1
    counts.antiSenseReads shouldBe 1
  }

  @Test
  def testSamRecordStrand: Unit = {
    val readPlusUnpaired = BaseCounterTest.lineParser.parseLine("r02\t0\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readMinUnpaired = BaseCounterTest.lineParser.parseLine("r02\t16\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readPlusPairedR1 = BaseCounterTest.lineParser.parseLine("r02\t73\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readMinPairedR1 = BaseCounterTest.lineParser.parseLine("r02\t89\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readPlusPairedR2 = BaseCounterTest.lineParser.parseLine("r02\t137\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readMinPairedR2 = BaseCounterTest.lineParser.parseLine("r02\t153\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")

    samRecordStrand(readPlusUnpaired, true) shouldBe false
    samRecordStrand(readMinUnpaired, true) shouldBe true
    samRecordStrand(readPlusPairedR1, true) shouldBe false
    samRecordStrand(readMinPairedR1, true) shouldBe true
    samRecordStrand(readPlusPairedR2, true) shouldBe true
    samRecordStrand(readMinPairedR2, true) shouldBe false

    samRecordStrand(readPlusUnpaired, false) shouldBe true
    samRecordStrand(readMinUnpaired, false) shouldBe false
    samRecordStrand(readPlusPairedR1, false) shouldBe true
    samRecordStrand(readMinPairedR1, false) shouldBe false
    samRecordStrand(readPlusPairedR2, false) shouldBe false
    samRecordStrand(readMinPairedR2, false) shouldBe true

    samRecordStrand(readPlusUnpaired, geneA) shouldBe false
    samRecordStrand(readMinUnpaired, geneA) shouldBe true
    samRecordStrand(readPlusPairedR1, geneA) shouldBe false
    samRecordStrand(readMinPairedR1, geneA) shouldBe true
    samRecordStrand(readPlusPairedR2, geneA) shouldBe true
    samRecordStrand(readMinPairedR2, geneA) shouldBe false

    samRecordStrand(readPlusUnpaired, geneC) shouldBe true
    samRecordStrand(readMinUnpaired, geneC) shouldBe false
    samRecordStrand(readPlusPairedR1, geneC) shouldBe true
    samRecordStrand(readMinPairedR1, geneC) shouldBe false
    samRecordStrand(readPlusPairedR2, geneC) shouldBe false
    samRecordStrand(readMinPairedR2, geneC) shouldBe true
  }

  @Test
  def testGeneCount: Unit = {
    val readPlus = BaseCounterTest.lineParser.parseLine("r02\t0\tchrQ\t101\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val readMin = BaseCounterTest.lineParser.parseLine("r02\t16\tchrQ\t101\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001")
    val geneCount = new GeneCount(geneA)

    geneCount.gene shouldBe geneA
    geneCount.transcripts.size shouldBe 1
    geneCount.transcripts.head.exonCounts.size shouldBe 4
    geneCount.transcripts.head.intronCounts.size shouldBe 3

    geneCount.addRecord(readPlus, samRecordStrand(readPlus, geneA))
    geneCount.exonCounts.map(_.counts.senseBases).sum shouldBe 0
    geneCount.exonCounts.map(_.counts.antiSenseBases).sum shouldBe 10
    geneCount.addRecord(readMin, samRecordStrand(readMin, geneA))
    geneCount.exonCounts.map(_.counts.senseBases).sum shouldBe 10
    geneCount.exonCounts.map(_.counts.antiSenseBases).sum shouldBe 10
  }
}

object BaseCounterTest {
  val lineParser = {
    val header = new SAMFileHeader
    header.addSequence(new SAMSequenceRecord("chrQ", 10000))
    header.addSequence(new SAMSequenceRecord("chrR", 10000))
    header.addReadGroup(new SAMReadGroupRecord("001"))

    new SAMLineParser(header)
  }

  val geneA = {
    val gene = new Gene("chrQ", 101, 200, false, "geneA")
    gene.addTranscript("A1", 101, 200, 111, 190, 4)
    for (transcript <- gene) {
      transcript.name match {
        case "A1" =>
          transcript.addExon(101, 120)
          transcript.addExon(131, 140)
          transcript.addExon(151, 160)
          transcript.addExon(171, 200)
      }
    }
    gene
  }

  val geneB = {
    val gene = new Gene("chrQ", 151, 250, false, "geneB")
    gene.addTranscript("A1", 151, 250, 161, 240, 4)
    for (transcript <- gene) {
      transcript.name match {
        case "A1" =>
          transcript.addExon(161, 170)
          transcript.addExon(181, 190)
          transcript.addExon(201, 210)
          transcript.addExon(221, 230)
      }
    }
    gene
  }

  val geneC = new Gene("chrQ", 101, 300, true, "geneC")
}