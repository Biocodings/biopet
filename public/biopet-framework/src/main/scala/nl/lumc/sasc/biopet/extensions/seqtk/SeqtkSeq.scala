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
package nl.lumc.sasc.biopet.extensions.seqtk

import java.io.File
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }
import nl.lumc.sasc.biopet.core.config.Configurable

/**
 * Wrapper for the seqtk seq subcommand.
 * Written based on seqtk version 1.0-r63-dirty.
 */
class SeqtkSeq(val root: Configurable) extends Seqtk {

  /** input file */
  @Input(doc = "Input file (FASTQ or FASTA)")
  var input: File = _

  /** output file */
  @Output(doc = "Output file")
  var output: File = _

  /** mask bases with quality lower than INT [0] */
  var q: Option[Int] = config("q")

  /** masked bases converted to CHAR; 0 for lowercase [0] */
  var n: String = config("n")

  /** number of residues per line; 0 for 2^32-1 [0] */
  var l: Option[Int] = config("l")

  /** quality shift: ASCII-INT gives base quality [33] */
  var Q: Option[Int] = config("Q")

  /** random seed (effective with -f) [11] */
  var s: Option[Int] = config("s")

  /** sample FLOAT fraction of sequences [1] */
  var f: Option[Int] = config("f")

  /** mask regions in BED or name list FILE [null] */
  var M: File = config("M")

  /** drop sequences with length shorter than INT [0] */
  var L: Option[Int] = config("L")

  /** mask complement region (effective with -M) */
  var c: Boolean = config("c")

  /** reverse complement */
  var r: Boolean = config("r")

  /** force FASTA output (discard quality) */
  var A: Boolean = config("A")

  /** drop comments at the header lines */
  var C: Boolean = config("C")

  /** drop sequences containing ambiguous bases */
  var N: Boolean = config("N")

  /** output the 2n-1 reads only */
  var flag1: Boolean = config("1")

  /** output the 2n reads only */
  var flag2: Boolean = config("2")

  /** shift quality by '(-Q) - 33' */
  var V: Boolean = config("V")

  def cmdLine = {
    required(executable) +
      " seq " +
      optional("-q", q) +
      optional("-n", n) +
      optional("-l", l) +
      optional("-Q", Q) +
      optional("-s", s) +
      optional("-f", f) +
      optional("-M", M) +
      optional("-L", L) +
      conditional(c, "-c") +
      conditional(r, "-r") +
      conditional(A, "-A") +
      conditional(C, "-C") +
      conditional(N, "-N") +
      conditional(flag1, "-1") +
      conditional(flag2, "-2") +
      conditional(V, "-V") +
      required(input) +
      " > " + required(output)
  }

  /**
   * Calculates the offset required for the -Q flag for format conversion (-V flag set).
   * This is required since seqtk computes the encoding offset indirectly from the input
   * and output offsets.
   *
   * @param  inQualOffset  ASCII offset of the input file encoding
   * @param  outQualOffset ASCII offset of the output file encoding
   * @return               the value to be used with the -Q flag with -V set
   */
  def calcQForV(inQualOffset: Int, outQualOffset: Int): Int = {
    // For the input for the -Q flag for seqtk, together with -V
    inQualOffset - (outQualOffset - 33)
  }
}