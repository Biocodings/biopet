package nl.lumc.sasc.biopet.core.report

import java.io.File
import java.nio.file.Paths

import com.google.common.io.Files
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{ DataProvider, Test }

/**
 * Created by pjvanthof on 24/02/16.
 */
class ReportBuilderTest extends TestNGSuite with Matchers {

  private def resourcePath(p: String): String = {
    Paths.get(getClass.getResource(p).toURI).toString
  }

  @DataProvider(name = "testGeneratePages")
  def generatePageProvider = {
    val sample = Array(Some("sampleName"), None)
    val lib = Array(Some("libName"), None)
    val nested = Array(false, true)
    for (s <- sample; l <- lib; n <- nested) yield Array(s, l, n)
  }

  @Test(dataProvider = "testGeneratePages")
  def testGeneratePages(sample: Option[String], lib: Option[String], nested: Boolean): Unit = {
    val builder = new ReportBuilder {
      def reportName: String = "test"
      def indexPage: ReportPage = ReportPage(
        (if (nested) "p1" -> ReportPage(Nil, Nil, Map()) :: Nil else Nil), Nil, Map())
    }

    val tempDir = Files.createTempDir()
    tempDir.deleteOnExit()
    val args = Array("-s", resourcePath("/empty_summary.json"), "-o", tempDir.getAbsolutePath) ++
      sample.map(x => Array("-a", s"sampleId=$x")).getOrElse(Array()) ++
      lib.map(x => Array("-a", s"libId=$x")).getOrElse(Array())
    builder.main(args)
    builder.sampleId shouldBe sample
    builder.libId shouldBe lib
    builder.extFiles.foreach(x => new File(tempDir, "ext" + File.separator + x.targetPath) should exist)
    new File(tempDir, "index.html") should exist
    new File(tempDir, "p1" + File.separator + "index.html").exists() shouldBe nested
  }

  @Test
  def testCountPages: Unit = {
    ReportBuilder.countPages(ReportPage(Nil, Nil, Map())) shouldBe 1
    ReportBuilder.countPages(ReportPage(
      "p1" -> ReportPage(Nil, Nil, Map()) :: Nil,
      Nil, Map())) shouldBe 2
    ReportBuilder.countPages(ReportPage(
      "p1" -> ReportPage(Nil, Nil, Map()) :: "p2" -> ReportPage(Nil, Nil, Map()) :: Nil,
      Nil, Map())) shouldBe 3
    ReportBuilder.countPages(ReportPage(
      "p1" -> ReportPage("p1" -> ReportPage(Nil, Nil, Map()) :: Nil, Nil, Map()) :: Nil,
      Nil, Map())) shouldBe 3
    ReportBuilder.countPages(ReportPage(
      "p1" -> ReportPage(Nil, Nil, Map()) :: "p2" -> ReportPage("p1" -> ReportPage(Nil, Nil, Map()) :: Nil, Nil, Map()) :: Nil,
      Nil, Map())) shouldBe 4
  }

  @Test
  def testRenderTemplate: Unit = {
    ReportBuilder.templateCache = Map()
    ReportBuilder.templateCache shouldBe empty
    ReportBuilder.renderTemplate("/template.ssp", Map("arg" -> "test")) shouldBe "test"
    ReportBuilder.templateCache.size shouldBe 1
    ReportBuilder.renderTemplate("/template.ssp", Map("arg" -> "bla")) shouldBe "bla"
    ReportBuilder.templateCache.size shouldBe 1
  }
}