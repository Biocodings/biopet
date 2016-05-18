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
package nl.lumc.sasc.biopet.pipelines.mapping

import java.io.{ File, FileOutputStream }

import com.google.common.io.Files
import nl.lumc.sasc.biopet.extensions.kraken.Kraken
import nl.lumc.sasc.biopet.pipelines.flexiprep.Fastqc
import nl.lumc.sasc.biopet.utils.ConfigUtils
import nl.lumc.sasc.biopet.utils.config.Config
import org.apache.commons.io.FileUtils
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{ AfterClass, BeforeClass, DataProvider, Test }

/**
 * Test class for [[Mapping]]
 *
 * Created by pjvan_thof on 2/12/15.
 */
abstract class AbstractTestMapping(val aligner: String) extends TestNGSuite with Matchers {
  def initPipeline(map: Map[String, Any]): Mapping = {
    new Mapping {
      override def configNamespace = "mapping"
      override def globalConfig = new Config(map)
      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  def paired = Array(true, false)
  def chunks = Array(1, 5)
  def skipMarkDuplicates = Array(true, false)
  def skipFlexipreps = Array(true, false)
  def zipped = Array(true, false)
  def unmappedToGears = false

  @DataProvider(name = "mappingOptions")
  def mappingOptions = {
    for (
      pair <- paired;
      chunk <- chunks;
      skipMarkDuplicate <- skipMarkDuplicates;
      skipFlexiprep <- skipFlexipreps;
      zipped <- zipped
    ) yield Array(aligner, pair, chunk, skipMarkDuplicate, skipFlexiprep, zipped)
  }

  @Test(dataProvider = "mappingOptions")
  def testMapping(aligner: String, paired: Boolean, chunks: Int,
                  skipMarkDuplicate: Boolean,
                  skipFlexiprep: Boolean,
                  zipped: Boolean) = {
    val map = ConfigUtils.mergeMaps(Map("output_dir" -> outputDir,
      "aligner" -> aligner,
      "number_chunks" -> chunks,
      "skip_markduplicates" -> skipMarkDuplicate,
      "skip_flexiprep" -> skipFlexiprep,
      "unmapped_to_gears" -> unmappedToGears
    ), Map(executables.toSeq: _*))
    val mapping: Mapping = initPipeline(map)

    if (zipped) {
      mapping.inputR1 = r1Zipped
      if (paired) mapping.inputR2 = Some(r2Zipped)
    } else {
      mapping.inputR1 = r1
      if (paired) mapping.inputR2 = Some(r2)
    }
    mapping.sampleId = Some("1")
    mapping.libId = Some("1")
    mapping.script()

    //Flexiprep
    mapping.functions.count(_.isInstanceOf[Fastqc]) shouldBe (if (skipFlexiprep) 0 else if (paired) 4 else 2)

    mapping.functions.count(_.isInstanceOf[Kraken]) shouldBe (if (unmappedToGears) 1 else 0)
  }

  val outputDir = Files.createTempDir()
  new File(outputDir, "input").mkdirs()

  val r1 = new File(outputDir, "input" + File.separator + "R1.fq")
  val r2 = new File(outputDir, "input" + File.separator + "R2.fq")
  val r1Zipped = new File(outputDir, "input" + File.separator + "R1.fq.gz")
  val r2Zipped = new File(outputDir, "input" + File.separator + "R2.fq.gz")

  @BeforeClass
  def createTempFiles: Unit = {
    Files.touch(r1)
    Files.touch(r2)
    Files.touch(r1Zipped)
    Files.touch(r2Zipped)

    copyFile("ref.fa")
    copyFile("ref.dict")
    copyFile("ref.fa.fai")
    copyFile("ref.1.bt2")
    copyFile("ref.1.ebwt")

  }

  private def copyFile(name: String): Unit = {
    val is = getClass.getResourceAsStream("/" + name)
    val os = new FileOutputStream(new File(outputDir, name))
    org.apache.commons.io.IOUtils.copy(is, os)
    os.close()
  }

  val executables = Map(
    "reference_fasta" -> (outputDir + File.separator + "ref.fa"),
    "db" -> "test",
    "bowtie_index" -> (outputDir + File.separator + "ref"),
    "fastqc" -> Map("exe" -> "test"),
    "seqtk" -> Map("exe" -> "test"),
    "gsnap" -> Map("exe" -> "test"),
    "tophat" -> Map("exe" -> "test"),
    "sickle" -> Map("exe" -> "test"),
    "cutadapt" -> Map("exe" -> "test"),
    "bwa" -> Map("exe" -> "test"),
    "star" -> Map("exe" -> "test"),
    "bowtie" -> Map("exe" -> "test"),
    "bowtie2" -> Map("exe" -> "test"),
    "stampy" -> Map("exe" -> "test", "genome" -> "test", "hash" -> "test"),
    "samtools" -> Map("exe" -> "test"),
    "kraken" -> Map("exe" -> "test", "db" -> "test"),
    "krakenreport" -> Map("exe" -> "test", "db" -> "test"),
    "md5sum" -> Map("exe" -> "test")
  )

  // remove temporary run directory all tests in the class have been run
  @AfterClass def removeTempOutputDir() = {
    FileUtils.deleteDirectory(outputDir)
  }
}

class MappingBwaMemTest extends AbstractTestMapping("bwa-mem")
class MappingBwaAlnTest extends AbstractTestMapping("bwa-aln")
class MappingStarTest extends AbstractTestMapping("star")
class MappingStar2PassTest extends AbstractTestMapping("star-2pass")
class MappingBowtieTest extends AbstractTestMapping("bowtie")
class MappingBowtie2Test extends AbstractTestMapping("bowtie2")
class MappingStampyTest extends AbstractTestMapping("stampy")
class MappingGsnapTest extends AbstractTestMapping("gsnap")
class MappingTophatTest extends AbstractTestMapping("tophat")

class MappingGearsTest extends AbstractTestMapping("bwa-mem") {
  override def unmappedToGears = true

  override def paired = Array(true)
  override def chunks = Array(1)
  override def skipMarkDuplicates = Array(true)
  override def skipFlexipreps = Array(true)
  override def zipped = Array(true)
}