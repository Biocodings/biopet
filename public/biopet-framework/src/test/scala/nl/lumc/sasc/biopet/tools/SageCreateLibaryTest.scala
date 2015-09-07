package nl.lumc.sasc.biopet.tools

import java.io.File
import java.nio.file.Paths

import org.biojava3.core.sequence.DNASequence
import org.biojava3.core.sequence.io.FastaReaderHelper
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

import scala.collection.JavaConversions._


import scala.io.Source

/**
 * Created by ahbbollen on 7-9-15.
 */
class SageCreateLibaryTest extends TestNGSuite with MockitoSugar with Matchers {

  import SageCreateLibrary._
  private def resourcePath(p: String): String = {
    Paths.get(getClass.getResource(p).toURI).toString
  }

  @Test
  def testMain = {

    val input = resourcePath("/mini.transcriptome.fa")
    val output = File.createTempFile("sageCreateLibrary", ".tsv")
    val noTagsOutput = File.createTempFile("sageCreateLibrary", ".tsv")
    val antiTagsOutput = File.createTempFile("sageCreateLibrary", ".tsv")
    val allGenesOutput = File.createTempFile("sageCreateLibrary", ".tsv")

    val args = Array("-I", input, "-o", output.getAbsolutePath, "--tag", "CATG",
      "--length", "17", "--noTagsOutput", noTagsOutput.getAbsolutePath, "--noAntiTagsOutput",
      antiTagsOutput.getAbsolutePath,"--allGenesOutput", allGenesOutput.getAbsolutePath)

    noException should be thrownBy main(args)

    val args2 = Array("-I", input, "-o", output.getAbsolutePath, "--tag", "CATG",
      "--length", "17")
    noException should be thrownBy main(args2)
    val args3 = Array("-I", input, "-o", output.getAbsolutePath, "--tag", "CATG",
      "--length", "17", "--noTagsOutput", noTagsOutput.getAbsolutePath)
    noException should be thrownBy main(args3)

  }

  @Test
  def testOutPut = {
    val input = resourcePath("/mini.transcriptome.fa")
    val output = File.createTempFile("sageCreateLibrary", ".tsv")
    val noTagsOutput = File.createTempFile("sageCreateLibrary", ".tsv")
    val antiTagsOutput = File.createTempFile("sageCreateLibrary", ".tsv")
    val allGenesOutput = File.createTempFile("sageCreateLibrary", ".tsv")

    val args = Array("-I", input, "-o", output.getAbsolutePath, "--tag", "CATG",
      "--length", "17", "--noTagsOutput", noTagsOutput.getAbsolutePath, "--noAntiTagsOutput",
      antiTagsOutput.getAbsolutePath,"--allGenesOutput", allGenesOutput.getAbsolutePath)
    main(args)

    Source.fromFile(output).mkString should equal(
      Source.fromFile(new File(resourcePath("/sageTest.tsv"))).mkString
    )

    Source.fromFile(noTagsOutput).mkString should equal (
      Source.fromFile(new File(resourcePath("/sageNoTagsTest.tsv"))).mkString
    )

    Source.fromFile(antiTagsOutput).mkString should equal (
      Source.fromFile(new File(resourcePath("/sageNoAntiTest.tsv"))).mkString
    )

    Source.fromFile(allGenesOutput).mkString should equal (
      Source.fromFile(new File(resourcePath("/sageAllGenesTest.tsv"))).mkString
    )
  }

  @Test
  def testGetTags = {
    val input = resourcePath("/mini.transcriptome.fa")

    val reader = FastaReaderHelper.readFastaDNASequence(new File(input))

    val records = reader.iterator.toList
    tagRegex = ("CATG" + "[CATG]{" + 17 + "}").r

    val record1 = records(0)
    val record2 = records(1)
    val record3 = records(2)

    val result1 = getTags(record1._1, record1._2, tagRegex)
    val result2  = getTags(record2._1, record2._2, tagRegex)
    val result3 = getTags(record3._1, record3._2, tagRegex)

    result1.allTags.size shouldBe 2
    result1.allAntiTags.size shouldBe 2
    result1.firstTag shouldBe "CATGGATTGCGCTCTACTGGT"
    result1.firstAntiTag shouldBe "CATGGTTCCCAGTGTGAGAAC"

    result2.allTags.size shouldBe 2
    result2.allAntiTags.size shouldBe 2
    result2.firstTag shouldBe "CATGTTCTTCCTTAGCACCCT"
    result2.firstAntiTag shouldBe "CATGGGTGGAACCCTTAAAAC"

    result3.allTags.size shouldBe 0
    result3.allAntiTags.size shouldBe 0
  }



}
