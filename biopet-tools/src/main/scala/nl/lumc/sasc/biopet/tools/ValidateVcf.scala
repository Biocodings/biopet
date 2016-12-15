package nl.lumc.sasc.biopet.tools

import java.io.File

import htsjdk.variant.vcf.VCFFileReader
import nl.lumc.sasc.biopet.utils.ToolCommand
import nl.lumc.sasc.biopet.utils.intervals.BedRecordList

import scala.collection.JavaConversions._

/**
 * Created by pjvanthof on 10/12/2016.
 */
object ValidateVcf extends ToolCommand {
  case class Args(inputVcf: File = null,
                  reference: File = null,
                  failOnError: Boolean = true) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('i', "inputVcf") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(inputVcf = x)
    } text "Vcf file to check"
    opt[File]('R', "reference") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(reference = x)
    } text "Reference fasta to check vcf file against"
    opt[Unit]("disableFail") maxOccurs 1 valueName "<file>" action { (x, c) =>
      c.copy(failOnError = false)
    } text "Do not fail on error. The tool will still exit when encountering an error, but will do so with exit code 0"
  }

  def main(args: Array[String]): Unit = {
    logger.info("Start")

    val argsParser = new OptParser
    val cmdArgs: Args = argsParser.parse(args, Args()) getOrElse (throw new IllegalArgumentException)

    val regions = BedRecordList.fromReference(cmdArgs.reference)

    val vcfReader = new VCFFileReader(cmdArgs.inputVcf, false)

    try {
      for (record <- vcfReader.iterator()) {
        val contig = record.getContig
        require(regions.chrRecords.contains(contig),
          s"The following contig in the vcf file does not exist in the reference: ${contig}")
        val start = record.getStart
        val end = record.getEnd
        val contigStart = regions.chrRecords(contig).head.start
        val contigEnd = regions.chrRecords(contig).head.end
        require(start >= contigStart && start <= contigEnd,
          s"The following position does not exist on reference: ${contig}:$start")
        if (end != start) require(end >= contigStart && end <= contigEnd,
          s"The following position does not exist on reference: ${contig}:$end")
        require(start <= end, "End location of variant is larger than start position. This should not be possible")
      }
    } catch {
      case e: IllegalArgumentException =>
        if (cmdArgs.failOnError) throw e
        else logger.error(e.getMessage)
    }

    vcfReader.close()

    logger.info("Done")
  }
}