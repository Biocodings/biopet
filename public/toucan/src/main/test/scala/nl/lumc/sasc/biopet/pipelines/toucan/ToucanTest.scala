package nl.lumc.sasc.biopet.pipelines.toucan

import java.io.File
import java.nio.file.Paths

import com.google.common.io.Files
import nl.lumc.sasc.biopet.extensions.VariantEffectPredictor
import nl.lumc.sasc.biopet.extensions.tools.VcfWithVcf
import nl.lumc.sasc.biopet.utils.config.Config
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

/**
 * Created by pjvan_thof on 4/11/16.
 */
class ToucanTest extends TestNGSuite with Matchers {
  def initPipeline(map: Map[String, Any]): Toucan = {
    new Toucan {
      override def configNamespace = "toucan"
      override def globalConfig = new Config(map)
      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  @Test
  def testDefault(): Unit = {
    val pipeline = initPipeline(ToucanTest.config)
    pipeline.inputVcf = new File(ToucanTest.resourcePath("/chrQ2.vcf.gz"))
    pipeline.script()

    pipeline.functions.count(_.isInstanceOf[VariantEffectPredictor]) shouldBe 1
    pipeline.functions.count(_.isInstanceOf[VcfWithVcf]) shouldBe 0
  }

  @Test
  def testBinning(): Unit = {
    val pipeline = initPipeline(ToucanTest.config ++ Map("bin_size" -> 4000))
    pipeline.inputVcf = new File(ToucanTest.resourcePath("/chrQ2.vcf.gz"))
    pipeline.script()

    pipeline.functions.count(_.isInstanceOf[VariantEffectPredictor]) shouldBe 4
    pipeline.functions.count(_.isInstanceOf[VcfWithVcf]) shouldBe 0
  }

  @Test
  def testGonl(): Unit = {
    val pipeline = initPipeline(ToucanTest.config ++ Map("gonl_vcf" -> ToucanTest.gonlVcfFile))
    pipeline.inputVcf = new File(ToucanTest.resourcePath("/chrQ2.vcf.gz"))
    pipeline.script()

    pipeline.functions.count(_.isInstanceOf[VariantEffectPredictor]) shouldBe 1
    pipeline.functions.count(_.isInstanceOf[VcfWithVcf]) shouldBe 1
  }

  @Test
  def testExac(): Unit = {
    val pipeline = initPipeline(ToucanTest.config ++ Map("exac_vcf" -> ToucanTest.exacVcfFile))
    pipeline.inputVcf = new File(ToucanTest.resourcePath("/chrQ2.vcf.gz"))
    pipeline.script()

    pipeline.functions.count(_.isInstanceOf[VariantEffectPredictor]) shouldBe 1
    pipeline.functions.count(_.isInstanceOf[VcfWithVcf]) shouldBe 1
  }

  @Test
  def testVarda(): Unit = {
    val pipeline = initPipeline(ToucanTest.config ++ Map("use_varda" -> true))
    val gvcfFile = File.createTempFile("bla.", ".g.vcf")
    pipeline.inputVcf = new File(ToucanTest.resourcePath("/chrQ2.vcf.gz"))
    pipeline.inputGvcf = Some(gvcfFile)
    pipeline.script()

    pipeline.functions.count(_.isInstanceOf[VariantEffectPredictor]) shouldBe 1
    pipeline.functions.count(_.isInstanceOf[VcfWithVcf]) shouldBe 0
  }

}

object ToucanTest {
  private def resourcePath(p: String): String = {
    Paths.get(getClass.getResource(p).toURI).toString
  }

  val outputDir = Files.createTempDir()
  outputDir.deleteOnExit()

  val gonlVcfFile: File = File.createTempFile("gonl.", ".vcf.gz")
  gonlVcfFile.deleteOnExit()
  val exacVcfFile: File = File.createTempFile("exac.", ".vcf.gz")
  exacVcfFile.deleteOnExit()

  val config = Map(
    "reference_fasta" -> resourcePath("/fake_chrQ.fa"),
    "output_dir" -> outputDir,
    "gatk_jar" -> "test",
    "varianteffectpredictor" -> Map(
      "vep_script" -> "test",
      "cache" -> true,
      "dir" -> "test"
    ),
    "varda_root" -> "test",
    "varda_token" -> "test",
    "bcftools" -> Map("exe" -> "test"),
    "bedtools" -> Map("exe" -> "test"),
    "manwe" -> Map("exe" -> "test")
  )
}
