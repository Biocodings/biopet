package nl.lumc.sasc.biopet.utils.intervals

import java.io.File

import htsjdk.samtools.util.Interval

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.Source

/**
 * Created by pjvan_thof on 8/20/15.
 */
class BedRecordList(val chrRecords: Map[String, List[BedRecord]]) {
  def allRecords = for (chr <- chrRecords; record <- chr._2) yield record

  def sort = new BedRecordList(chrRecords.map(x => x._1 -> x._2.sortBy(_.start)))

  def overlapWith(record: BedRecord) = chrRecords
      .getOrElse(record.chr, Nil)
      .dropWhile(_.end < record.start)
      .takeWhile(_.start <= record.end)
}

object BedRecordList {
  def fromList(records: Traversable[BedRecord]): BedRecordList = fromList(records.toIterator)

  def fromList(records: TraversableOnce[BedRecord]): BedRecordList = {
    val map = mutable.Map[String, List[BedRecord]]()
    for (record <- records)
      map += record.chr -> (record :: map.getOrElse(record.chr, List()))
    new BedRecordList(map.toMap)
  }

  def fromFile(bedFile: File) = {
    fromList(Source.fromFile(bedFile).getLines().map(BedRecord.fromLine(_)))
  }

  def combineOverlap(list: BedRecordList): BedRecordList = {
    new BedRecordList(for ((chr, records) <- list.sort.chrRecords) yield chr -> {
      def combineOverlap(records: List[BedRecord],
                                 newRecords: ListBuffer[BedRecord] = ListBuffer()): List[BedRecord] = {
        if (records.nonEmpty) {
          val chr = records.head.chr
          val start = records.head.start
          val overlapRecords = records.takeWhile(_.start <= records.head.end)
          val end = overlapRecords.map(_.end).max

          newRecords += BedRecord(chr, start, end)
          combineOverlap(records.drop(overlapRecords.length), newRecords)
        } else newRecords.toList
      }
      combineOverlap(records)
    })
  }
}