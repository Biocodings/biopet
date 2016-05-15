/**
 * Due to the license issue with GATK, this part of Biopet can only be used inside the
 * LUMC. Please refer to https://git.lumc.nl/biopet/biopet/wikis/home for instructions
 * on how to use this protected part of biopet or contact us at sasc@lumc.nl
 */
package nl.lumc.sasc.biopet.pipelines.shiva

import java.io.{File, FileOutputStream}

import com.google.common.io.Files
import nl.lumc.sasc.biopet.core.BiopetPipe
import nl.lumc.sasc.biopet.extensions.Freebayes
import nl.lumc.sasc.biopet.extensions.bcftools.{BcftoolsCall, BcftoolsMerge}
import nl.lumc.sasc.biopet.extensions.gatk.{CombineVariants, GenotypeConcordance, HaplotypeCaller, UnifiedGenotyper}
import nl.lumc.sasc.biopet.utils.config.Config
import nl.lumc.sasc.biopet.extensions.tools.{MpileupToVcf, VcfFilter, VcfStats}
import nl.lumc.sasc.biopet.extensions.vt.{VtDecompose, VtNormalize}
import nl.lumc.sasc.biopet.utils.ConfigUtils
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{DataProvider, Test}

import scala.collection.mutable.ListBuffer

/**
 * Class for testing ShivaVariantcalling
 *
 * Created by pjvan_thof on 3/2/15.
 */
trait ShivaVariantcallingTestTrait extends TestNGSuite with Matchers {
  def initPipeline(map: Map[String, Any]): ShivaVariantcalling = {
    new ShivaVariantcalling() {
      override def configNamespace = "shivavariantcalling"
      override def globalConfig = new Config(ConfigUtils.mergeMaps(map, ShivaVariantcallingTest.config))
      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  def raw: Boolean = false
  def bcftools: Boolean = false
  def bcftools_singlesample: Boolean = false
  def haplotypeCallerGvcf: Boolean = false
  def haplotypeCallerAllele: Boolean = false
  def unifiedGenotyperAllele: Boolean = false
  def unifiedGenotyper: Boolean = false
  def haplotypeCaller: Boolean = false
  def freebayes: Boolean = false
  def varscanCnsSinglesample: Boolean = false
  def referenceVcf: Option[File] = None

  def normalize = false
  def decompose = false

  @DataProvider(name = "shivaVariantcallingOptions")
  def shivaVariantcallingOptions = {
    (for (bams <- 0 to 2) yield Array[Any](bams, raw, bcftools, bcftools_singlesample, unifiedGenotyper,
      haplotypeCaller, haplotypeCallerGvcf, haplotypeCallerAllele, unifiedGenotyperAllele,
      freebayes, varscanCnsSinglesample)
    ).toArray
  }

  @Test(dataProvider = "shivaVariantcallingOptions")
  def testShivaVariantcalling(bams: Int,
                              raw: Boolean,
                              bcftools: Boolean,
                              bcftoolsSinglesample: Boolean,
                              unifiedGenotyper: Boolean,
                              haplotypeCaller: Boolean,
                              haplotypeCallerGvcf: Boolean,
                              haplotypeCallerAllele: Boolean,
                              unifiedGenotyperAllele: Boolean,
                              freebayes: Boolean,
                              varscanCnsSinglesample: Boolean) = {
    val callers: ListBuffer[String] = ListBuffer()
    if (raw) callers.append("raw")
    if (bcftools) callers.append("bcftools")
    if (bcftoolsSinglesample) callers.append("bcftools_singlesample")
    if (unifiedGenotyper) callers.append("unifiedgenotyper")
    if (haplotypeCallerGvcf) callers.append("haplotypecaller_gvcf")
    if (haplotypeCallerAllele) callers.append("haplotypecaller_allele")
    if (unifiedGenotyperAllele) callers.append("unifiedgenotyper_allele")
    if (haplotypeCaller) callers.append("haplotypecaller")
    if (freebayes) callers.append("freebayes")
    if (varscanCnsSinglesample) callers.append("varscan_cns_singlesample")
    val map = Map(
      "variantcallers" -> callers.toList,
      "execute_vt_normalize" -> normalize,
      "execute_vt_decompose" -> decompose
    ) ++ referenceVcf.map("reference_vcf" -> _)
    val pipeline = initPipeline(map)

    pipeline.inputBams = (for (n <- 1 to bams) yield n.toString -> ShivaVariantcallingTest.inputTouch("bam_" + n + ".bam")).toMap

    val illegalArgumentException = pipeline.inputBams.isEmpty || callers.isEmpty

    if (illegalArgumentException) intercept[IllegalArgumentException] {
      pipeline.script()
    }

    if (!illegalArgumentException) {
      pipeline.script()

      val pipesJobs = pipeline.functions.filter(_.isInstanceOf[BiopetPipe]).flatMap(_.asInstanceOf[BiopetPipe].pipesJobs)

      pipeline.functions.count(_.isInstanceOf[CombineVariants]) shouldBe (1 + (if (raw) 1 else 0) + (if (varscanCnsSinglesample) 1 else 0))
      pipesJobs.count(_.isInstanceOf[BcftoolsCall]) shouldBe (if (bcftools) 1 else 0) + (if (bcftoolsSinglesample) bams else 0)
      pipeline.functions.count(_.isInstanceOf[BcftoolsMerge]) shouldBe (if (bcftoolsSinglesample && bams > 1) 1 else 0)
      pipesJobs.count(_.isInstanceOf[Freebayes]) shouldBe (if (freebayes) 1 else 0)
      pipesJobs.count(_.isInstanceOf[MpileupToVcf]) shouldBe (if (raw) bams else 0)
      pipeline.functions.count(_.isInstanceOf[VcfFilter]) shouldBe (if (raw) bams else 0)
      pipeline.functions.count(_.isInstanceOf[HaplotypeCaller]) shouldBe (if (haplotypeCaller) 1 else 0) +
        (if (haplotypeCallerAllele) 1 else 0) + (if (haplotypeCallerGvcf) bams else 0)
      pipeline.functions.count(_.isInstanceOf[UnifiedGenotyper]) shouldBe (if (unifiedGenotyper) 1 else 0) +
        (if (unifiedGenotyperAllele) 1 else 0)
      pipeline.functions.count(_.isInstanceOf[VcfStats]) shouldBe (1 + callers.size)
      pipeline.functions.count(_.isInstanceOf[VtNormalize]) shouldBe (if (normalize) callers.size else 0)
      pipeline.functions.count(_.isInstanceOf[VtDecompose]) shouldBe (if (decompose) callers.size else 0)
      pipeline.functions.count(_.isInstanceOf[GenotypeConcordance]) shouldBe (if (referenceVcf.isDefined) 1 else 0)
    }
  }
}

class ShivaVariantcallingNoVariantcallersTest extends ShivaVariantcallingTestTrait
class ShivaVariantcallingAllTest extends ShivaVariantcallingTestTrait {
  override def raw: Boolean = true
  override def bcftools: Boolean = true
  override def bcftools_singlesample: Boolean = true
  override def haplotypeCallerGvcf: Boolean = true
  override def haplotypeCallerAllele: Boolean = true
  override def unifiedGenotyperAllele: Boolean = true
  override def unifiedGenotyper: Boolean = true
  override def haplotypeCaller: Boolean = true
  override def freebayes: Boolean = true
  override def varscanCnsSinglesample: Boolean = true
}
class ShivaVariantcallingRawTest extends ShivaVariantcallingTestTrait {
  override def raw: Boolean = true
}
class ShivaVariantcallingBcftoolsTest extends ShivaVariantcallingTestTrait {
  override def bcftools: Boolean = true
}
class ShivaVariantcallingBcftoolsSinglesampleTest extends ShivaVariantcallingTestTrait {
  override def bcftools_singlesample: Boolean = true
}
class ShivaVariantcallingHaplotypeCallerGvcfTest extends ShivaVariantcallingTestTrait {
  override def haplotypeCallerGvcf: Boolean = true
}
class ShivaVariantcallingHaplotypeCallerAlleleTest extends ShivaVariantcallingTestTrait {
  override def haplotypeCallerAllele: Boolean = true
}
class ShivaVariantcallingUnifiedGenotyperAlleleTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyperAllele: Boolean = true
}
class ShivaVariantcallingUnifiedGenotyperTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyper: Boolean = true
}
class ShivaVariantcallingHaplotypeCallerTest extends ShivaVariantcallingTestTrait {
  override def haplotypeCaller: Boolean = true
}
class ShivaVariantcallingFreebayesTest extends ShivaVariantcallingTestTrait {
  override def freebayes: Boolean = true
}
class ShivaVariantcallingVarscanCnsSinglesampleTest extends ShivaVariantcallingTestTrait {
  override def varscanCnsSinglesample: Boolean = true
}
class ShivaVariantcallingNormalizeTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyper: Boolean = true
  override def normalize = true
}
class ShivaVariantcallingDecomposeTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyper: Boolean = true
  override def decompose = true
}
class ShivaVariantcallingNormalizeDecomposeTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyper: Boolean = true
  override def normalize = true
  override def decompose = true
}
class ShivaVariantcallingUReferenceVcfTest extends ShivaVariantcallingTestTrait {
  override def unifiedGenotyper: Boolean = true
  override def referenceVcf = Some(new File("ref.vcf"))
}

object ShivaVariantcallingTest {
  val outputDir = Files.createTempDir()
  outputDir.deleteOnExit()
  new File(outputDir, "input").mkdirs()
  def inputTouch(name: String): File = {
    val file = new File(outputDir, "input" + File.separator + name).getAbsoluteFile
    Files.touch(file)
    file
  }

  private def copyFile(name: String): Unit = {
    val is = getClass.getResourceAsStream("/" + name)
    val os = new FileOutputStream(new File(outputDir, name))
    org.apache.commons.io.IOUtils.copy(is, os)
    os.close()
  }

  copyFile("ref.fa")
  copyFile("ref.dict")
  copyFile("ref.fa.fai")

  val config = Map(
    "name_prefix" -> "test",
    "output_dir" -> outputDir,
    "cache" -> true,
    "dir" -> "test",
    "vep_script" -> "test",
    "reference_fasta" -> (outputDir + File.separator + "ref.fa"),
    "gatk_jar" -> "test",
    "samtools" -> Map("exe" -> "test"),
    "bcftools" -> Map("exe" -> "test"),
    "md5sum" -> Map("exe" -> "test"),
    "bgzip" -> Map("exe" -> "test"),
    "tabix" -> Map("exe" -> "test"),
    "input_alleles" -> "test.vcf.gz",
    "varscan_jar" -> "test",
    "vt" -> Map("exe" -> "test")
  )
}