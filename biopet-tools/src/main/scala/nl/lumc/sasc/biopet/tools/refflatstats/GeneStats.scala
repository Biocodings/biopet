package nl.lumc.sasc.biopet.tools.refflatstats

/**
  * Created by pjvanthof on 01/05/2017.
  */
case class GeneStats(name: String,
                     totalGc: Double,
                     exonGc: Double,
                     transcripts: Array[TranscriptStats]) {

}
