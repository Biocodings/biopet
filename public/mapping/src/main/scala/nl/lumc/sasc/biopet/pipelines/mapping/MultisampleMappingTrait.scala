package nl.lumc.sasc.biopet.pipelines.mapping

import java.io.File

import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.reference.FastaSequenceFile
import nl.lumc.sasc.biopet.core.report.ReportBuilderExtension
import nl.lumc.sasc.biopet.core.{ PipelineCommand, Reference, MultiSampleQScript }
import nl.lumc.sasc.biopet.extensions.Ln
import nl.lumc.sasc.biopet.extensions.picard._
import nl.lumc.sasc.biopet.pipelines.bammetrics.BamMetrics
import nl.lumc.sasc.biopet.pipelines.bamtobigwig.Bam2Wig
import nl.lumc.sasc.biopet.pipelines.gears.GearsSingle
import nl.lumc.sasc.biopet.utils.Logging
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.QScript

import MultisampleMapping.MergeStrategy

import scala.collection.JavaConversions._

/**
 * Created by pjvanthof on 18/12/15.
 */
trait MultisampleMappingTrait extends MultiSampleQScript
  with Reference { qscript: QScript =>

  /** With this method the merge strategy for libraries to samples is defined. This can be overriden to fix the merge strategy. */
  def mergeStrategy: MergeStrategy.Value = {
    val value: String = config("merge_strategy", default = "preprocessmarkduplicates")
    MergeStrategy.values.find(_.toString.toLowerCase == value) match {
      case Some(v) => v
      case _       => throw new IllegalArgumentException(s"merge_strategy '$value' does not exist")
    }
  }

  def init(): Unit = {
  }

  /** Is there are jobs that needs to be added before the rest of the jobs this methods can be overriden, to let the sample jobs this work the super call should be done also */
  def biopetScript(): Unit = {
    addSamplesJobs()
    addSummaryJobs()
  }

  /** This is de default multisample mapping report, this can be extended by other pipelines */
  override def reportClass: Option[ReportBuilderExtension] = {
    val report = new MultisampleMappingReport(this)
    report.outputDir = new File(outputDir, "report")
    report.summaryFile = summaryFile
    Some(report)
  }

  override def defaults = super.defaults ++ Map("reordersam" -> Map("allow_incomplete_dict_concordance" -> true))

  /** In a default multisample mapping run there are no multsample jobs. This method can be overriden by other pipelines */
  def addMultiSampleJobs(): Unit = {
    // this code will be executed after all code of all samples is executed
  }

  /** By default only the reference is put in the summary, when extending pippeline specific files can be added */
  def summaryFiles: Map[String, File] = Map("referenceFasta" -> referenceFasta())

  /** By default only the reference is put in the summary, when extending pippeline specific settings can be added */
  def summarySettings: Map[String, Any] = Map(
    "reference" -> referenceSummary,
    "merge_strategy" -> mergeStrategy.toString)

  def makeSample(id: String) = new Sample(id)
  class Sample(sampleId: String) extends AbstractSample(sampleId) {

    def makeLibrary(id: String) = new Library(id)
    class Library(libId: String) extends AbstractLibrary(libId) {

      /** By default the bams files are put in the summary, more files can be added here */
      def summaryFiles: Map[String, File] = (inputR1.map("input_R1" -> _) :: inputR2.map("input_R2" -> _) ::
        inputBam.map("input_bam" -> _) :: bamFile.map("output_bam" -> _) ::
        preProcessBam.map("output_bam_preprocess" -> _) :: Nil).flatten.toMap

      def summaryStats: Map[String, Any] = Map()

      lazy val inputR1: Option[File] = MultisampleMapping.fileMustBeAbsolute(config("R1"))
      lazy val inputR2: Option[File] = MultisampleMapping.fileMustBeAbsolute(config("R2"))
      lazy val inputBam: Option[File] = MultisampleMapping.fileMustBeAbsolute(if (inputR1.isEmpty) config("bam") else None)
      lazy val bamToFastq: Boolean = config("bam_to_fastq", default = false)
      lazy val correctReadgroups: Boolean = config("correct_readgroups", default = false)

      lazy val mapping = if (inputR1.isDefined || (inputBam.isDefined && bamToFastq)) {
        val m = new Mapping(qscript)
        m.sampleId = Some(sampleId)
        m.libId = Some(libId)
        m.outputDir = libDir
        Some(m)
      } else None

      def bamFile = mapping match {
        case Some(m)                 => Some(m.finalBamFile)
        case _ if inputBam.isDefined => Some(new File(libDir, s"$sampleId-$libId.bam"))
        case _                       => None
      }

      /** By default the preProcessBam is the same as the normal bamFile. A pipeline can extend this is there are preprocess steps */
      def preProcessBam = bamFile

      /** This method can be extended to add jobs to the pipeline, to do this the super call of this function must be called by the pipelines */
      def addJobs(): Unit = {
        inputR1.foreach(inputFiles :+= new InputFile(_, config("R1_md5")))
        inputR2.foreach(inputFiles :+= new InputFile(_, config("R2_md5")))
        inputBam.foreach(inputFiles :+= new InputFile(_, config("bam_md5")))

        if (inputR1.isDefined) {
          mapping.foreach { m =>
            m.input_R1 = inputR1.get
            m.input_R2 = inputR2
            add(m)
          }
        } else if (inputBam.isDefined) {
          if (bamToFastq) {
            val samToFastq = SamToFastq(qscript, inputBam.get,
              new File(libDir, sampleId + "-" + libId + ".R1.fq.gz"),
              new File(libDir, sampleId + "-" + libId + ".R2.fq.gz"))
            samToFastq.isIntermediate = true
            qscript.add(samToFastq)
            mapping.foreach(m => {
              m.input_R1 = samToFastq.fastqR1
              m.input_R2 = Some(samToFastq.fastqR2)
              add(m)
            })
          } else {
            val inputSam = SamReaderFactory.makeDefault.open(inputBam.get)
            val header = inputSam.getFileHeader
            val readGroups = header.getReadGroups
            val referenceFile = new FastaSequenceFile(referenceFasta(), true)
            val dictOke: Boolean = {
              var oke = true
              try {
                header.getSequenceDictionary.assertSameDictionary(referenceFile.getSequenceDictionary)
              } catch {
                case e: AssertionError => {
                  println(e.getMessage)
                  oke = false
                }
              }
              oke
            }
            inputSam.close()
            referenceFile.close()

            val readGroupOke = readGroups.forall(readGroup => {
              if (readGroup.getSample != sampleId) logger.warn("Sample ID readgroup in bam file is not the same")
              if (readGroup.getLibrary != libId) logger.warn("Library ID readgroup in bam file is not the same")
              readGroup.getSample == sampleId && readGroup.getLibrary == libId
            })

            if (!readGroupOke || !dictOke) {
              if (!readGroupOke && !correctReadgroups) Logging.addError(
                "Sample readgroup and/or library of input bamfile is not correct, file: " + bamFile +
                  "\nPlease note that it is possible to set 'correct_readgroups' to true in the config to automatic fix this")
              if (!dictOke) Logging.addError(
                "Sequence dictionary in the bam file is not the same as the reference, file: " + bamFile +
                  "\nPlease note that it is possible to set 'correct_dict' to true in the config to automatic fix this")

              if (!readGroupOke && correctReadgroups) {
                logger.info("Correcting readgroups, file:" + inputBam.get)
                val aorrg = AddOrReplaceReadGroups(qscript, inputBam.get, bamFile.get)
                aorrg.RGID = config("rgid", default = s"$sampleId-$libId")
                aorrg.RGLB = libId
                aorrg.RGSM = sampleId
                aorrg.RGPL = config("rgpl", default = "unknown")
                aorrg.RGPU = config("rgpu", default = "na")
                aorrg.isIntermediate = true
                qscript.add(aorrg)
              }
            } else add(Ln.linkBamFile(qscript, inputBam.get, bamFile.get): _*)

            val bamMetrics = new BamMetrics(qscript)
            bamMetrics.sampleId = Some(sampleId)
            bamMetrics.libId = Some(libId)
            bamMetrics.inputBam = bamFile.get
            bamMetrics.outputDir = new File(libDir, "metrics")
            add(bamMetrics)

            if (config("execute_bam2wig", default = true)) add(Bam2Wig(qscript, bamFile.get))
          }
        } else logger.warn(s"Sample '$sampleId' does not have any input files")
      }
    }

    /** By default the bams files are put in the summary, more files can be added here */
    def summaryFiles: Map[String, File] = (bamFile.map("output_bam" -> _) ::
      preProcessBam.map("output_bam_preprocess" -> _) :: Nil).flatten.toMap

    def summaryStats: Map[String, Any] = Map()

    /** This is the merged bam file, None if the merged bam file is NA */
    def bamFile = if (libraries.flatMap(_._2.bamFile).nonEmpty &&
      mergeStrategy != MultisampleMapping.MergeStrategy.None)
      Some(new File(sampleDir, s"$sampleId.bam"))
    else None

    /** By default the preProcessBam is the same as the normal bamFile. A pipeline can extend this is there are preprocess steps */
    def preProcessBam = bamFile

    /** Default is set to keep the merged files, user can set this in the config. To change the default this method can be overriden */
    def keepMergedFiles: Boolean = config("keep_merged_files", default = true)

    /** This method can be extended to add jobs to the pipeline, to do this the super call of this function must be called by the pipelines */
    def addJobs(): Unit = {
      addPerLibJobs() // This add jobs for each library

      mergeStrategy match {
        case MergeStrategy.None =>
        case (MergeStrategy.MergeSam | MergeStrategy.MarkDuplicates) if libraries.flatMap(_._2.bamFile).size == 1 =>
          add(Ln.linkBamFile(qscript, libraries.flatMap(_._2.bamFile).head, bamFile.get): _*)
        case (MergeStrategy.PreProcessMergeSam | MergeStrategy.PreProcessMarkDuplicates) if libraries.flatMap(_._2.preProcessBam).size == 1 =>
          add(Ln.linkBamFile(qscript, libraries.flatMap(_._2.preProcessBam).head, bamFile.get): _*)
        case MergeStrategy.MergeSam =>
          add(MergeSamFiles(qscript, libraries.flatMap(_._2.bamFile).toList, bamFile.get, isIntermediate = !keepMergedFiles))
        case MergeStrategy.PreProcessMergeSam =>
          add(MergeSamFiles(qscript, libraries.flatMap(_._2.preProcessBam).toList, bamFile.get, isIntermediate = !keepMergedFiles))
        case MergeStrategy.MarkDuplicates =>
          add(MarkDuplicates(qscript, libraries.flatMap(_._2.bamFile).toList, bamFile.get, isIntermediate = !keepMergedFiles))
        case MergeStrategy.PreProcessMarkDuplicates =>
          add(MarkDuplicates(qscript, libraries.flatMap(_._2.preProcessBam).toList, bamFile.get, isIntermediate = !keepMergedFiles))
        case _ => throw new IllegalStateException("This should not be possible, unimplemented MergeStrategy?")
      }

      if (mergeStrategy != MergeStrategy.None && libraries.flatMap(_._2.bamFile).nonEmpty) {
        val bamMetrics = new BamMetrics(qscript)
        bamMetrics.sampleId = Some(sampleId)
        bamMetrics.inputBam = preProcessBam.get
        bamMetrics.outputDir = new File(sampleDir, "metrics")
        add(bamMetrics)

        if (config("execute_bam2wig", default = true)) add(Bam2Wig(qscript, preProcessBam.get))
      }

      if (config("unmapped_to_gears", default = false) && libraries.flatMap(_._2.bamFile).nonEmpty) {
        val gears = new GearsSingle(qscript)
        gears.bamFile = preProcessBam
        gears.sampleId = Some(sampleId)
        gears.outputDir = new File(sampleDir, "gears")
        add(gears)
      }
    }
  }
}

/** This class is the default implementation that can be used on the command line */
class MultisampleMapping(val root: Configurable) extends QScript with MultisampleMappingTrait {
  def this() = this(null)

  def summaryFile: File = new File(outputDir, "MultisamplePipeline.summary.json")
}

object MultisampleMapping extends PipelineCommand {

  object MergeStrategy extends Enumeration {
    val None, MergeSam, MarkDuplicates, PreProcessMergeSam, PreProcessMarkDuplicates = Value
  }

  /** When file is not absolute an error is raise att the end of the script of a pipeline */
  def fileMustBeAbsolute(file: Option[File]): Option[File] = {
    if (file.forall(_.isAbsolute)) file
    else {
      Logging.addError(s"$file should be a absolute file path")
      file.map(_.getAbsoluteFile)
    }
  }

}
