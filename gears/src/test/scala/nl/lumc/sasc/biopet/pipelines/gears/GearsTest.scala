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
package nl.lumc.sasc.biopet.pipelines.gears

import java.io.File

import com.google.common.io.Files
import nl.lumc.sasc.biopet.utils.ConfigUtils
import nl.lumc.sasc.biopet.utils.config.Config
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{ DataProvider, Test }

/**
 * Created by pjvanthof on 04/02/16.
 */
abstract class GearsTest extends TestNGSuite with Matchers {
  def initPipeline(map: Map[String, Any]): Gears = {
    new Gears {
      override def configNamespace = "gears"

      override def globalConfig = new Config(map)

      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  def kraken: Option[Boolean] = None
  def qiimeClosed: Boolean = false
  def qiimeOpen: Boolean = false
  def qiimeRtax: Boolean = false
  def seqCount: Boolean = false
  def libraryGears: Boolean = false

  @DataProvider(name = "gearsOptions")
  def gearsOptions = {
    val bool = Array(true, false)

    for (
      s1 <- bool; s2 <- bool
    ) yield Array("", s1, s2)
  }

  @Test(dataProvider = "gearsOptions")
  def testGears(dummy: String, sample1: Boolean, sample2: Boolean): Unit = {
    val map = {
      var m: Map[String, Any] = GearsTest.config
      if (sample1) m = ConfigUtils.mergeMaps(GearsTest.sample1, m)
      if (sample2) m = ConfigUtils.mergeMaps(GearsTest.sample2, m)
      ConfigUtils.mergeMaps(Map(
        "gears_use_qiime_rtax" -> qiimeRtax,
        "gears_use_qiime_closed" -> qiimeClosed,
        "gears_use_qiime_open" -> qiimeOpen,
        "gears_use_seq_count" -> seqCount,
        "library_gears" -> libraryGears,
        "output_dir" -> TestGearsSingle.outputDir
      ) ++ kraken.map("gears_use_kraken" -> _), m)
    }

    if (!sample1 && !sample2) { // When no samples
      intercept[IllegalArgumentException] {
        initPipeline(map).script()
      }
    } else {
      val pipeline = initPipeline(map)
      pipeline.script()

    }
  }
}

class GearsDefaultTest extends GearsTest
class GearsKrakenTest extends GearsTest {
  override def kraken = Some(true)
}
class GearsQiimeClosedTest extends GearsTest {
  override def kraken = Some(false)
  override def qiimeClosed = true
}
class GearsQiimeOpenTest extends GearsTest {
  override def kraken = Some(false)
  override def qiimeOpen = true
}
class GearsLibraryTest extends GearsTest {
  override def libraryGears = true
}

object GearsTest {
  val outputDir = Files.createTempDir()
  new File(outputDir, "input").mkdirs()
  outputDir.deleteOnExit()

  val r1 = new File(outputDir, "input" + File.separator + "R1.fq")
  Files.touch(r1)
  val r2 = new File(outputDir, "input" + File.separator + "R2.fq")
  Files.touch(r2)
  val bam = new File(outputDir, "input" + File.separator + "bamfile.bam")
  Files.touch(bam)

  val config = Map(
    "output_dir" -> outputDir,
    "kraken" -> Map("exe" -> "test", "db" -> "test"),
    "krakenreport" -> Map("exe" -> "test", "db" -> "test"),
    "sambamba" -> Map("exe" -> "test"),
    "mergeotutables" -> Map("exe" -> "test"),
    "samtools" -> Map("exe" -> "test"),
    "md5sum" -> Map("exe" -> "test"),
    "assigntaxonomy" -> Map("exe" -> "test"),
    "pickclosedreferenceotus" -> Map("exe" -> "test"),
    "pickotus" -> Map("exe" -> "test"),
    "pickrepset" -> Map("exe" -> "test"),
    "splitlibrariesfastq" -> Map("exe" -> "test"),
    "flash" -> Map("exe" -> "test"),
    "fastqc" -> Map("exe" -> "test"),
    "seqtk" -> Map("exe" -> "test"),
    "sickle" -> Map("exe" -> "test"),
    "cutadapt" -> Map("exe" -> "test"),
    "pickopenreferenceotus" -> Map("exe" -> "test")
  )

  val sample1 = Map(
    "samples" -> Map("sample1" -> Map("libraries" -> Map(
      "lib1" -> Map(
        "R1" -> r1.getAbsolutePath,
        "R2" -> r2.getAbsolutePath
      )
    )
    )))

  val sample2 = Map(
    "samples" -> Map("sample3" -> Map("libraries" -> Map(
      "lib1" -> Map(
        "R1" -> r1.getAbsolutePath,
        "R2" -> r2.getAbsolutePath
      ),
      "lib2" -> Map(
        "R1" -> r1.getAbsolutePath,
        "R2" -> r2.getAbsolutePath
      )
    )
    )))
}
