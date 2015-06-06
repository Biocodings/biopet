package nl.lumc.sasc.biopet.core.summary

import java.io.File

import nl.lumc.sasc.biopet.utils.ConfigUtils

/**
 * Created by pjvan_thof on 3/26/15.
 */
class Summary(file: File) {
  val map = ConfigUtils.fileToConfigMap(file)

  lazy val samples: Set[String] = {
    ConfigUtils.getValueFromPath(map, List("samples")) match {
      case Some(samples) => ConfigUtils.any2map(samples).keySet
      case _             => Set()
    }
  }

  lazy val libraries: Map[String, Set[String]] = {
    (for (sample <- samples) yield sample -> {
      ConfigUtils.getValueFromPath(map, List("samples", sample, "libraries")) match {
        case Some(libs) => ConfigUtils.any2map(libs).keySet
        case _          => Set[String]()
      }
    }).toMap
  }

  def getValue(path: String*): Option[Any] = {
    ConfigUtils.getValueFromPath(map, path.toList)
  }

  def getSampleValue(sampleId: String, path: String*): Option[Any] = {
    ConfigUtils.getValueFromPath(map, "samples" :: sampleId :: path.toList)
  }

  def getSampleValues(path: String*): Map[String, Option[Any]] = {
    (for (sample <- samples) yield sample -> getSampleValue(sample, path: _*)).toMap
  }

  def getSampleValues(function: (Summary, String) => Option[Any]): Map[String, Option[Any]] = {
    (for (sample <- samples) yield sample -> function(this, sample)).toMap
  }

  def getLibraryValue(sampleId: String, libId: String, path: String*): Option[Any] = {
    ConfigUtils.getValueFromPath(map, "samples" :: sampleId :: "libraries" :: libId :: path.toList)
  }

  def getLibraryValue(sampleId: Option[String], libId: Option[String], path: String*): Option[Any] = {
    (sampleId, libId) match {
      case (Some(sample), Some(lib)) => getLibraryValue(sample, lib, path: _*)
      case (Some(sample), _)         => getSampleValue(sample, path: _*)
      case _                         => getValue(path: _*)
    }
  }

  def getLibraryValues(path: String*): Map[(String, String), Option[Any]] = {
    (for (sample <- samples; lib <- libraries.getOrElse(sample, Set())) yield {
      (sample, lib) -> getLibraryValue(sample, lib, path: _*)
    }).toMap
  }

  def getLibraryValues(function: (Summary, String, String) => Option[Any]): Map[(String, String), Option[Any]] = {
    (for (sample <- samples; lib <- libraries.getOrElse(sample, Set())) yield {
      (sample, lib) -> function(this, sample, lib)
    }).toMap
  }
}
