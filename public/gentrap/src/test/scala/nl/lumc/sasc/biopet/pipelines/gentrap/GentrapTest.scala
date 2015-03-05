/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.biopet.pipelines.gentrap

import java.io.File

import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{ AfterClass, DataProvider, Test }

import nl.lumc.sasc.biopet.core.config.Config
import nl.lumc.sasc.biopet.extensions._
import nl.lumc.sasc.biopet.pipelines.gentrap.extensions._
import nl.lumc.sasc.biopet.pipelines.gentrap.scripts._
import nl.lumc.sasc.biopet.tools.{ MergeTables, WipeReads }
import nl.lumc.sasc.biopet.utils.ConfigUtils

class GentrapTest extends TestNGSuite with Matchers {

  def initPipeline(map: Map[String, Any]): Gentrap = {
    new Gentrap() {
      override def configName = "gentrap"
      override def globalConfig = new Config(map)
      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  private lazy val validExpressionMeasures = Set(
    "fragments_per_gene", "fragments_per_exon", "bases_per_gene", "bases_per_exon",
    "cufflinks_strict", "cufflinks_guided", "cufflinks_blind")

  @DataProvider(name = "gentrapOptions")
  def flexiprepOptions = {

    //val paired = Array(true, false)
    val paired = Array(false)
    val expressionMeasures = validExpressionMeasures
      .subsets
      .map(_.toList)
      .toArray

    for (
      pair <- paired;
      expressionMeasure <- expressionMeasures
    ) yield Array("", pair, expressionMeasure)
  }

  @Test(dataProvider = "gentrapOptions")
  def testGentrap(name: String, paired: Boolean, expMeasures: List[String]) = {

    val map = ConfigUtils.mergeMaps(
      Map(
        "output_dir" -> GentrapTest.outputDir,
        "gsnap" -> Map("db" -> "test", "dir" -> "test"),
        "aligner" -> "gsnap",
        "expression_measures" -> expMeasures,
        "strand_protocol" -> "non_specific",
        "samples" -> Map(
          "sample_1" -> Map(
            "libraries" -> Map(
              "lib_1" -> Map(
                "R1" -> "test_R1.fq"
              )
            )
          )
        )
      ),
      Map(GentrapTest.executables.toSeq: _*))
    val gentrap: Gentrap = initPipeline(map)

    gentrap.script()

    gentrap.functions.count(_.isInstanceOf[Gsnap]) shouldBe 1
  }

  // remove temporary run directory all tests in the class have been run
  @AfterClass def removeTempOutputDir() = {
    FileUtils.deleteDirectory(GentrapTest.outputDir)
  }
}

object GentrapTest {
  val outputDir = Files.createTempDir()

  val executables = Map(
    "reference" -> "test",
    "annotation_gtf" -> "test",
    "annotation_bed" -> "test",
    "annotation_refflat" -> "test",
    "stampy" -> Map("exe" -> "test", "genome" -> "test", "hash" -> "test")
  ) ++ Seq(
      // fastqc executables
      "fastqc", "seqtk", "sickle", "cutadapt",
      // mapping executables
      "bwa", "star", "bowtie", "samtools", "gsnap",
      // gentrap executables
      "cufflinks", "htseq-count", "grep", "pdflatex", "Rscript", "tabix", "bgzip"
    ).map { case exe => exe -> Map("exe" -> "test") }.toMap
}
