package nl.lumc.sasc.biopet.tools

import java.io.File
import java.nio.file.Paths

import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

import scala.io.Source

/**
 * Created by ahbbollen on 7-9-15.
 */
class SageCreateTagCountsTest extends TestNGSuite with MockitoSugar with Matchers {

  import SageCreateTagCounts._
  private def resourcePath(p: String): String = {
    Paths.get(getClass.getResource(p).toURI).toString
  }

  @Test
  def testMain = {
    val input = resourcePath("/tagCount.tsv")
    val tagLib = resourcePath("/sageTest.tsv")

    val sense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val allSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val antiSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val allAntiSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")

    noException should be thrownBy main(Array("-I", input, "--tagLib", tagLib,
    "--countSense", sense.getAbsolutePath, "--countAllSense", allSense.getAbsolutePath,
    "--countAntiSense", antiSense.getAbsolutePath, "--countAllAntiSense", allAntiSense.getAbsolutePath))
    noException should be thrownBy main(Array("-I", input, "--tagLib", tagLib,
      "--countSense", sense.getAbsolutePath, "--countAllSense", allSense.getAbsolutePath,
      "--countAntiSense", antiSense.getAbsolutePath))
    noException should be thrownBy main(Array("-I", input, "--tagLib", tagLib,
      "--countSense", sense.getAbsolutePath, "--countAllSense", allSense.getAbsolutePath))
    noException should be thrownBy main(Array("-I", input, "--tagLib", tagLib,
      "--countSense", sense.getAbsolutePath))
    noException should be thrownBy main(Array("-I", input, "--tagLib", tagLib))

  }

  @Test
  def testOutput = {
    val input = resourcePath("/tagCount.tsv")
    val tagLib = resourcePath("/sageTest.tsv")

    val sense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val allSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val antiSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")
    val allAntiSense = File.createTempFile("SageCreateTagCountsTEst", ".tsv")

    main(Array("-I", input, "--tagLib", tagLib,"--countSense", sense.getAbsolutePath,
      "--countAllSense", allSense.getAbsolutePath,"--countAntiSense", antiSense.getAbsolutePath,
      "--countAllAntiSense", allAntiSense.getAbsolutePath))

    Source.fromFile(sense).mkString should equal ("ENSG00000254767\t40\nENSG00000255336\t55\n")
    Source.fromFile(allSense).mkString should equal ("ENSG00000254767\t70\nENSG00000255336\t90\n")
    Source.fromFile(antiSense).mkString should equal ("ENSG00000254767\t50\nENSG00000255336\t45\n")
    Source.fromFile(allAntiSense).mkString should equal ("ENSG00000254767\t75\nENSG00000255336\t65\n")
  }






}
