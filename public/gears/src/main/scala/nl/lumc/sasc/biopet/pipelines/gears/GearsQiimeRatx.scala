package nl.lumc.sasc.biopet.pipelines.gears

import nl.lumc.sasc.biopet.core.BiopetQScript
import nl.lumc.sasc.biopet.extensions.qiime.{ PickRepSet, PickOtus }
import nl.lumc.sasc.biopet.utils.config.Configurable
import org.broadinstitute.gatk.queue.QScript

/**
 * Created by pjvan_thof on 12/4/15.
 */
class GearsQiimeRatx(val root: Configurable) extends QScript with BiopetQScript {

  var fastaR1: File = _

  var fastqR2: Option[File] = None

  def init() = {
    require(fastaR1 != null)
  }

  def biopetScript() = {
    val pickOtus = new PickOtus(this)
    pickOtus.inputFasta = fastaR1
    pickOtus.outputDir = new File(outputDir, "pick_otus")
    add(pickOtus)

    val pickRepSet = new PickRepSet(this)
    pickRepSet.outputDir = new File(outputDir, "pick_rep_set")
    pickRepSet.inputFile = pickOtus.otusTxt
    pickRepSet.outputFasta = Some(new File(pickRepSet.outputDir, fastaR1.getName))
    pickRepSet.logFile = Some(new File(pickRepSet.outputDir, fastaR1.getName
      .stripSuffix(".fasta").stripSuffix(".fa").stripSuffix(".fna") + ".log"))
    add(pickRepSet)
  }
}
