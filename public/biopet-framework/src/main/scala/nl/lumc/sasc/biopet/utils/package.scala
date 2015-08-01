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
package nl.lumc.sasc.biopet

import scala.util.{ Failure, Success, Try }

/**
 * General utility functions.
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package object utils {

  /** Regular expression for matching entire integer numbers (numbers without decimals / fractions) */
  val isInteger = """^([-+]?\d+)L?$""".r

  /** Regular expression for matching entire decimal numbers (compatible with the scientific notation) */
  val isDecimal = """^([-+]?\d*\.?\d+(?:[eE][-+]?[0-9]+)?)$""".r

  /**
   * Tries to convert the given string with the given conversion functions recursively.
   *
   * If conversion is successful, the converted object is returned within as a [[Success]] type. Otherwise, a [[Failure]]
   * is returned. The order of conversion functions is the same as the order they are specified.
   *
   * @param raw the string to convert.
   * @param funcs one or more conversion functions to apply.
   * @return a [[Try]] object encapsulating the conversion result.
   */
  def tryToConvert(raw: String, funcs: (String => Any)*): Try[Any] = {
    if (funcs.isEmpty) Try(throw new Exception(s"Can not extract value from string $raw"))
    else Try(funcs.head(raw))
      .transform(s => Success(s), f => tryToConvert(raw, funcs.tail: _*))
  }

  /**
   * Tries to convert the given string into the appropriate number representation.
   *
   * The given string must be whole numbers without any preceeding or succeeding whitespace. This function takes
   * into account the maximum values of the number object to use. For example, if the raw string represents a bigger
   * number than the maximum [[Int]] value, then a [[Long]] will be used. If the number is still bigger than a [[Long]],
   * the [[BigInt]] class will be used. The same is applied for decimal numbers, where the conversion order is first
   * a [[Double]], then a [[BigDecimal]].
   *
   * @param raw the string to convert.
   * @param fallBack Allows also to return the string itself when converting fails, default false.
   * @return a [[Try]] object encapsulating the conversion result.
   */
  def tryToParseNumber(raw: String, fallBack: Boolean = false) = raw match {
    case isInteger(i)  => tryToConvert(i, x => x.toInt, x => x.toLong, x => BigInt(x))
    case isDecimal(f)  => tryToConvert(f, x => x.toDouble, x => BigDecimal(x))
    case _ if fallBack => Try(raw)
    case _             => Try(throw new Exception(s"Can not extract number from string $raw"))
  }
}
