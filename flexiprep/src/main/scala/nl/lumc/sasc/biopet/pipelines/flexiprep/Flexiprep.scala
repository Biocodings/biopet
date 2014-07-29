package nl.lumc.sasc.biopet.pipelines.flexiprep

import scala.util.parsing.json._

import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk._
import org.broadinstitute.gatk.queue.extensions.picard._
import org.broadinstitute.gatk.queue.function._
import org.broadinstitute.gatk.utils.commandline.{ Input, Argument }

import nl.lumc.sasc.biopet.core._
import nl.lumc.sasc.biopet.core.config._
import nl.lumc.sasc.biopet.extensions._
import nl.lumc.sasc.biopet.extensions.fastq._
import nl.lumc.sasc.biopet.pipelines.flexiprep.scripts._

class Flexiprep(val root: Configurable) extends QScript with BiopetQScript {
  def this() = this(null)

  @Input(doc = "R1 fastq file (gzipped allowed)", shortName = "R1", required = true)
  var input_R1: File = _

  @Input(doc = "R2 fastq file (gzipped allowed)", shortName = "R2", required = false)
  var input_R2: File = _

  @Argument(doc = "Skip Trim fastq files", shortName = "skiptrim", required = false)
  var skipTrim: Boolean = false

  @Argument(doc = "Skip Clip fastq files", shortName = "skipclip", required = false)
  var skipClip: Boolean = false

  @Argument(doc = "Skip summary", shortName = "skipsummary", required = false)
  var skipSummary: Boolean = false

  var paired: Boolean = (input_R2 != null)
  var R1_ext: String = _
  var R2_ext: String = _
  var R1_name: String = _
  var R2_name: String = _

  def init() {
    for (file <- configfiles) globalConfig.loadConfigFile(file)
    if (!skipTrim) skipTrim = config("skiptrim", default = false)
    if (!skipClip) skipClip = config("skipclip", default = false)
    if (input_R1 == null) throw new IllegalStateException("Missing R1 on flexiprep module")
    if (outputDir == null) throw new IllegalStateException("Missing Output directory on flexiprep module")
    else if (!outputDir.endsWith("/")) outputDir += "/"
    paired = (input_R2 != null)

    if (input_R1.endsWith(".gz")) R1_name = input_R1.getName.substring(0, input_R1.getName.lastIndexOf(".gz"))
    else if (input_R1.endsWith(".gzip")) R1_name = input_R1.getName.substring(0, input_R1.getName.lastIndexOf(".gzip"))
    else R1_name = input_R1.getName
    R1_ext = R1_name.substring(R1_name.lastIndexOf("."), R1_name.size)
    R1_name = R1_name.substring(0, R1_name.lastIndexOf(R1_ext))

    if (paired) {
      if (input_R2.endsWith(".gz")) R2_name = input_R2.getName.substring(0, input_R2.getName.lastIndexOf(".gz"))
      else if (input_R2.endsWith(".gzip")) R2_name = input_R2.getName.substring(0, input_R2.getName.lastIndexOf(".gzip"))
      else R2_name = input_R2.getName
      R2_ext = R2_name.substring(R2_name.lastIndexOf("."), R2_name.size)
      R2_name = R2_name.substring(0, R2_name.lastIndexOf(R2_ext))
    }
  }

  def biopetScript() {
    runInitialJobs()

    if (paired) runTrimClip(outputFiles("fastq_input_R1"), outputFiles("fastq_input_R2"), outputDir)
    else runTrimClip(outputFiles("fastq_input_R1"), outputDir)

    runFinalize(List(outputFiles("output_R1")), if (outputFiles.contains("output_R2")) List(outputFiles("output_R2")) else List())
  }

  def runInitialJobs() {
    outputFiles += ("fastq_input_R1" -> extractIfNeeded(input_R1, outputDir))
    if (paired) outputFiles += ("fastq_input_R2" -> extractIfNeeded(input_R2, outputDir))

    var fastqc_R1 = Fastqc(this, input_R1, outputDir + "/" + R1_name + ".fastqc/")
    add(fastqc_R1)
    outputFiles += ("fastqc_R1" -> fastqc_R1.output)
    outputFiles += ("qualtype_R1" -> getQualtype(fastqc_R1, R1_name))
    outputFiles += ("contams_R1" -> getContams(fastqc_R1, R1_name))
    
    addSeqstat(outputFiles("fastq_input_R1"), "seqstat_R1", fastqc_R1)
    addSha1sum(outputFiles("fastq_input_R1"), "sha1_R1")
    
    if (paired) {
      var fastqc_R2 = Fastqc(this, input_R2, outputDir + "/" + R2_name + ".fastqc/")
      add(fastqc_R2)
      outputFiles += ("fastqc_R2" -> fastqc_R2.output)
      outputFiles += ("qualtype_R2" -> getQualtype(fastqc_R2, R2_name))
      outputFiles += ("contams_R2" -> getContams(fastqc_R2, R2_name))
      
      addSeqstat(outputFiles("fastq_input_R2"), "seqstat_R2", fastqc_R2)
      addSha1sum(outputFiles("fastq_input_R2"), "sha1_R2")
    }
  }

  def getQualtype(fastqc: Fastqc, pairname: String): File = {
    val fastqcToQualtype = new FastqcToQualtype(this)
    fastqcToQualtype.fastqc_output = fastqc.output
    fastqcToQualtype.out = new File(outputDir + pairname + ".qualtype.txt")
    add(fastqcToQualtype)
    return fastqcToQualtype.out
  }

  def getContams(fastqc: Fastqc, pairname: String): File = {
    val fastqcToContams = new FastqcToContams(this)
    fastqcToContams.fastqc_output = fastqc.output
    fastqcToContams.out = new File(outputDir + pairname + ".contams.txt")
    fastqcToContams.contams_file = fastqc.contaminants
    add(fastqcToContams)
    return fastqcToContams.out
  }

  def runTrimClip(R1_in: File, outDir: String, chunk: String) {
    runTrimClip(R1_in, new File(""), outDir, chunk)
  }
  def runTrimClip(R1_in: File, outDir: String) {
    runTrimClip(R1_in, new File(""), outDir, "")
  }
  def runTrimClip(R1_in: File, R2_in: File, outDir: String) {
    runTrimClip(R1_in, R2_in, outDir, "")
  }
  def runTrimClip(R1_in: File, R2_in: File, outDir: String, chunkarg: String): (File, File) = {
    val chunk = if (chunkarg.isEmpty || chunkarg.endsWith("_")) chunkarg else chunkarg + "_"
    var results: Map[String, File] = Map()

    var R1: File = new File(R1_in)
    var R2: File = new File(R2_in)

    if (!skipClip) { // Adapter clipping

      val cutadapt_R1 = new Cutadapt(this)

      if (!skipTrim || paired) cutadapt_R1.isIntermediate = true
      cutadapt_R1.fastq_input = R1
      cutadapt_R1.fastq_output = swapExt(outDir, R1, R1_ext, ".clip" + R1_ext)
      cutadapt_R1.stats_output = swapExt(outDir, R1, R1_ext, ".clip.stats")

      if (outputFiles.contains("contams_R1")) cutadapt_R1.contams_file = outputFiles("contams_R1")

      add(cutadapt_R1)
      R1 = cutadapt_R1.fastq_output

      if (paired) {
        val cutadapt_R2 = new Cutadapt(this)
        if (!skipTrim || paired) cutadapt_R2.isIntermediate = true
        cutadapt_R2.fastq_input = R2
        cutadapt_R2.fastq_output = swapExt(outDir, R2, R2_ext, ".clip" + R2_ext)
        if (outputFiles.contains("contams_R2")) cutadapt_R2.contams_file = outputFiles("contams_R2")
        add(cutadapt_R2)
        R2 = cutadapt_R2.fastq_output
        val fastqSync = new FastqSync(this)
        if (!skipTrim) fastqSync.isIntermediate = true
        fastqSync.deps ++= Seq(outputFiles("fastq_input_R1"), outputFiles("fastq_input_R2"))
        fastqSync.input_start_fastq = cutadapt_R1.fastq_input
        fastqSync.input_R1 = cutadapt_R1.fastq_output
        fastqSync.input_R2 = cutadapt_R2.fastq_output
        fastqSync.output_R1 = swapExt(outDir, R1, R1_ext, ".sync" + R1_ext)
        fastqSync.output_R2 = swapExt(outDir, R2, R2_ext, ".sync" + R2_ext)
        fastqSync.output_stats = swapExt(outDir, R1, R1_ext, ".sync.stats")
        add(fastqSync)
        outputFiles += ("syncStats" -> fastqSync.output_stats)
        R1 = fastqSync.output_R1
        R2 = fastqSync.output_R2
      }
    }

    if (!skipTrim) { // Quality trimming
      val sickle = new Sickle(this)
      sickle.input_R1 = R1
      sickle.deps :+= outputFiles("fastq_input_R1")
      sickle.output_R1 = swapExt(outDir, R1, R1_ext, ".trim" + R1_ext)
      if (outputFiles.contains("qualtype_R1")) sickle.qualityTypeFile = outputFiles("qualtype_R1")
      if (!skipClip) sickle.deps :+= R1_in
      if (paired) {
        sickle.deps :+= outputFiles("fastq_input_R2")
        sickle.input_R2 = R2
        sickle.output_R2 = swapExt(outDir, R2, R2_ext, ".trim" + R2_ext)
        sickle.output_singles = swapExt(outDir, R2, R2_ext, ".trim.singles" + R1_ext)
        if (!skipClip) sickle.deps :+= R2_in
      }
      sickle.output_stats = swapExt(outDir, R1, R1_ext, ".trim.stats")
      add(sickle)
      R1 = sickle.output_R1
      if (paired) R2 = sickle.output_R2
    }

    outputFiles += (chunk + "output_R1" -> R1)
    if (paired) outputFiles += (chunk + "output_R2" -> R2)
    return (R1, R2)
  }

  def runFinalize(fastq_R1: List[File], fastq_R2: List[File]) {
    if (fastq_R1.length != fastq_R2.length && paired) throw new IllegalStateException("R1 and R2 file number is not the same")
    var R1: File = ""
    var R2: File = ""
    if (fastq_R1.length == 1) {
      for (file <- fastq_R1) R1 = file
      for (file <- fastq_R2) R2 = file
    } else {
      R1 = new File(outputDir + R1_name + ".qc" + R1_ext)
      logger.debug(fastq_R1)
      add(Cat(this, fastq_R1, R1), true)
      if (paired) {
        logger.debug(fastq_R2)
        R2 = new File(outputDir + R2_name + ".qc" + R2_ext)
        add(Cat(this, fastq_R2, R2), true)
      }
    }

    if (fastq_R1.length == 1 && !config("skip_native_link", default = false)) {
      val lnR1 = new Ln(this)
      lnR1.in = R1
      R1 = new File(outputDir + R1_name + ".qc" + R1_ext)
      lnR1.out = R1
      add(lnR1)
      if (paired) {
        val lnR2 = new Ln(this)
        lnR2.in = R2
        R2 = new File(outputDir + R2_name + ".qc" + R2_ext)
        lnR2.out = R2
        add(lnR2)
      }
    }

    outputFiles += ("output_R1" -> R1)
    if (paired) outputFiles += ("output_R2" -> R2)

    if (!skipTrim || !skipClip) {
      addSeqstat(R1, "seqstat_qc_R1")
      if (paired) addSeqstat(R2, "seqstat_qc_R2")

      addSha1sum(R1, "sha1_qc_R1")
      if (paired) addSha1sum(R2, "sha1_qc_R2")
      val fastqc_R1 = Fastqc(this, outputFiles("output_R1"), outputDir + "/" + R1_name + ".qc.fastqc/")
      add(fastqc_R1)
      outputFiles += ("fastqc_R1_final" -> fastqc_R1.output)
      if (paired) {
        val fastqc_R2 = Fastqc(this, outputFiles("output_R2"), outputDir + "/" + R2_name + ".qc.fastqc/")
        add(fastqc_R2)
        outputFiles += ("fastqc_R2_final" -> fastqc_R2.output)
      }
    }

    if (!skipSummary) {
      val summarize = new Summarize(this)
      summarize.runDir = outputDir
      summarize.samplea = R1_name
      if (paired) summarize.sampleb = R2_name
      summarize.samplename = R1_name
      summarize.clip = !skipClip
      summarize.trim = !skipTrim
      summarize.out = new File(outputDir + R1_name + ".summary.json")
      for ((k, v) <- outputFiles) summarize.deps +:= v
      add(summarize)
    }
  }

  def extractIfNeeded(file: File, runDir: String): File = {
    if (file.getName().endsWith(".gz") || file.getName().endsWith(".gzip")) {
      var newFile: File = swapExt(runDir, file, ".gz", "")
      if (file.getName().endsWith(".gzip")) newFile = swapExt(runDir, file, ".gzip", "")
      val zcatCommand = Zcat(this, file, newFile)
      zcatCommand.isIntermediate = true
      add(zcatCommand)
      return newFile
    } else if (file.getName().endsWith(".bz2")) {
      var newFile = swapExt(runDir, file, ".bz2", "")
      val pbzip2 = Pbzip2(this, file, newFile)
      pbzip2.isIntermediate = true
      add(pbzip2)
      return newFile
    } else return file
  }
  
  def addSeqstat(fastq: File, key: String, fastqc:Fastqc = null) {
    val ext = fastq.getName.substring(fastq.getName.lastIndexOf("."))
    val seqstat = new Seqstat(this)
    seqstat.input_fastq = fastq
    seqstat.fastqc = fastqc
    seqstat.out = swapExt(outputDir, fastq, ext, ".seqstats.json")
    if (fastqc != null) seqstat.deps ::= fastqc.output
    add(seqstat)
    outputFiles += (key -> seqstat.out)
  }

  def addSha1sum(fastq: File, key: String) {
    val ext = fastq.getName.substring(fastq.getName.lastIndexOf("."))
    val sha1sum = new Sha1sum(this)
    sha1sum.input = fastq
    sha1sum.output = swapExt(outputDir, fastq, ext, ".sha1")
    add(sha1sum)
    outputFiles += (key -> sha1sum.output)
  }
}

object Flexiprep extends PipelineCommand {
  override val pipeline = "/nl/lumc/sasc/biopet/pipelines/flexiprep/Flexiprep.class"
}
