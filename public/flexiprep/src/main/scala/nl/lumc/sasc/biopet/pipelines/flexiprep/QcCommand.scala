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
package nl.lumc.sasc.biopet.pipelines.flexiprep

import java.io.File

import nl.lumc.sasc.biopet.core.summary.{ SummaryQScript, Summarizable }
import nl.lumc.sasc.biopet.core.{ BiopetFifoPipe, BiopetCommandLineFunction }
import nl.lumc.sasc.biopet.extensions.{ Cat, Gzip, Sickle }
import nl.lumc.sasc.biopet.extensions.seqtk.SeqtkSeq
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Output, Input }

/**
 * Created by pjvan_thof on 9/22/15.
 */
class QcCommand(val root: Configurable, val fastqc: Fastqc) extends BiopetCommandLineFunction with Summarizable {

  val flexiprep = root match {
    case f: Flexiprep => f
    case _            => throw new IllegalArgumentException("This class may only be used inside Flexiprep")
  }

  @Input(required = true)
  var input: File = _

  @Output(required = true)
  var output: File = _

  var compress = true

  var read: String = _

  override def defaultCoreMemory = 2.0
  override def defaultThreads = 3

  val seqtk = new SeqtkSeq(root)
  var clip: Option[Cutadapt] = None
  var trim: Option[Sickle] = None
  lazy val outputCommand: BiopetCommandLineFunction = if (compress) {
    val gzip = Gzip(root)
    gzip.output = output
    gzip
  } else {
    val cat = Cat(root)
    cat.output = output
    cat
  }

  def jobs = (Some(seqtk) :: clip :: trim :: Some(outputCommand) :: Nil).flatten

  def summaryFiles = Map()

  def summaryStats = Map()

  override def addToQscriptSummary(qscript: SummaryQScript, name: String): Unit = {
    clip match {
      case Some(job) => qscript.addSummarizable(job, s"clipping_$read")
      case _         =>
    }
    trim match {
      case Some(job) => qscript.addSummarizable(job, s"trimming_$read")
      case _         =>
    }
  }

  override def beforeGraph(): Unit = {
    super.beforeGraph()
    require(read != null)
    deps ::= input
    outputFiles :+= output
  }

  override def beforeCmd(): Unit = {
    seqtk.input = input
    seqtk.output = new File(output.getParentFile, input.getName + ".seqtk.fq")
    seqtk.Q = fastqc.encoding match {
      case null => None
      case enc if enc.contains("Sanger / Illumina 1.9") => None
      case enc if enc.contains("Illumina <1.3") => Option(64)
      case enc if enc.contains("Illumina 1.3") => Option(64)
      case enc if enc.contains("Illumina 1.5") => Option(64)
      case _ => None
    }
    if (seqtk.Q.isDefined) seqtk.V = true
    addPipeJob(seqtk)

    clip = if (!flexiprep.skipClip) {
      val foundAdapters = fastqc.foundAdapters.map(_.seq)
      if (foundAdapters.nonEmpty) {
        val cutadapt = new Cutadapt(root, fastqc)
        cutadapt.fastq_input = seqtk.output
        cutadapt.fastq_output = new File(output.getParentFile, input.getName + ".cutadapt.fq")
        cutadapt.stats_output = new File(flexiprep.outputDir, s"${flexiprep.sampleId.getOrElse("x")}-${flexiprep.libId.getOrElse("x")}.$read.clip.stats")
        if (cutadapt.default_clip_mode == "3") cutadapt.opt_adapter ++= foundAdapters
        else if (cutadapt.default_clip_mode == "5") cutadapt.opt_front ++= foundAdapters
        else if (cutadapt.default_clip_mode == "both") cutadapt.opt_anywhere ++= foundAdapters
        addPipeJob(cutadapt)
        Some(cutadapt)
      } else None
    } else None

    trim = if (!flexiprep.skipTrim) {
      val sickle = new Sickle(root)
      sickle.output_stats = new File(flexiprep.outputDir, s"${flexiprep.sampleId.getOrElse("x")}-${flexiprep.libId.getOrElse("x")}.$read.trim.stats")
      sickle.input_R1 = clip match {
        case Some(c) => c.fastq_output
        case _       => seqtk.output
      }
      sickle.output_R1 = new File(output.getParentFile, input.getName + ".sickle.fq")
      addPipeJob(sickle)
      Some(sickle)
    } else None

    val outputFile = (clip, trim) match {
      case (_, Some(t)) => t.output_R1
      case (Some(c), _) => c.fastq_output
      case _            => seqtk.output
    }

    outputCommand match {
      case gzip: Gzip => outputFile :<: gzip
      case cat: Cat   => cat.input = outputFile :: Nil
    }

    seqtk.beforeGraph()
    clip.foreach(_.beforeGraph())
    trim.foreach(_.beforeGraph())
    outputCommand.beforeGraph()

    seqtk.beforeCmd()
    clip.foreach(_.beforeCmd())
    trim.foreach(_.beforeCmd())
    outputCommand.beforeCmd()
  }

  def cmdLine = {

    val cmd = (clip, trim) match {
      case (Some(c), Some(t)) => new BiopetFifoPipe(root, seqtk :: c :: t :: outputCommand :: Nil)
      case (Some(c), _)       => new BiopetFifoPipe(root, seqtk :: c :: outputCommand :: Nil)
      case (_, Some(t))       => new BiopetFifoPipe(root, seqtk :: t :: outputCommand :: Nil)
      case _                  => new BiopetFifoPipe(root, seqtk :: outputCommand :: Nil)
    }

    //val cmds = (Some(seqtk) :: clip :: trim :: Some(new Gzip(root)) :: Nil).flatten
    cmd.beforeGraph()
    cmd.commandLine
  }
}
