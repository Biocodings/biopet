# Gentrap

## Introduction

Gentrap (*generic transcriptome analysis pipeline*) is a general data analysis pipelines for quantifying expression levels from RNA-seq libraries generated using the Illumina machines. It was designed to be flexible, providing several aligners and quantification modes to choose from, with optional steps in between. It can be used to run different experiment configurations, from single sample runs to multiple sample runs containing multiple sequencing libraries. It can also do a very simple variant calling (using VarScan).

At the moment, Gentrap supports the following aligners:

1. GSNAP
2. TopHat

and the following quantification modes:

1. Fragment counts per gene (using HTSeq-count)
2. Base counts per gene
3. Base counts per exon
4. Cufflinks-style quantification, with a strict reference, with a reference as a guide, and/or without any references

You can also provide a `.refFlat` file containing ribosomal sequence coordinates to measure how many of your libraries originate from ribosomal sequences. Then, you may optionally remove those regions as well.

## Configuration File

As with other biopet pipelines, Gentrap relies on a JSON configuration file to run its analyses. There are two important parts here, the configuration for the samples (to determine the sample layout of your experiment) and the configuration for the pipeline settings (to determine which analyses are run).

### Sample Configuration

Samples are single experimental units whose expression you want to measure. They usually consist of a single sequencing library, but in some cases (for example when the experiment demands each sample have a minimum library depth) a single sample may contain multiple sequencing libraries as well. All this is can be configured using the correct JSON nesting, with the following pattern:

~~~
{
  "samples": {
    "sample_A": {
      "libraries": {
        "lib_01": {
          "R1": "/absolute/path/to/first/read/pair.fq",
          "R2": "/absolute/path/to/second/read/pair.fq"
        }
      }
    }
  }
}
~~~

In the example above, there is one sample (named `sample_A`) which contains one sequencing library (named `lib_01`). The library itself is paired end, with both `R1` and `R2` pointing to the location of the files in the file system. A more complicated example is the following:

~~~
{
  "samples": {
    "sample_X": {
      "libraries": {
        "lib_one": {
          "R1": "/absolute/path/to/first/read/pair.fq",
          "R2": "/absolute/path/to/second/read/pair.fq"
        }
      }
    },
    "sample_Y": {
      "libraries": {
        "lib_one": {
          "R1": "/absolute/path/to/first/read/pair.fq",
          "R2": "/absolute/path/to/second/read/pair.fq"
        }
        "lib_two": {
          "R1": "/absolute/path/to/first/read/pair.fq",
          "R2": "/absolute/path/to/second/read/pair.fq"
        }
      }
    }
  }
}
~~~

In this case, we have two samples (`sample_X` and `sample_Y`) and `sample_Y` has two different libraries (`lib_one` and `lib_two`). Notice that the names of the samples and libraries may change, but several keys such as `samples`, `libraries`, `R1`, and `R2` remain the same.

### Pipeline Settings Configuration

For the pipeline settings, there are some values that you need to specify while some are optional. Required settings are:

1. `output_dir`: path to output directory (if it does not exist, Gentrap will create it for you).
2. `aligner`: which aligner to use (`gsnap` or `tophat`)
3. `reference`: this must point to a reference FASTA file and in the same directory, there must be a `.dict` file of the FASTA file.
4. `expression_measures`: this entry determines which expression measurement modes Gentrap will do. You can choose zero or more from the following: `fragments_per_gene`, `bases_per_gene`, `bases_per_exon`, `cufflinks_strict`, `cufflinks_guided`, and/or `cufflinks_blind`. If you only wish to align, you can set the value as an empty list (`[]`).
5. `strand_protocol`: this determines whether your library is prepared with a specific stranded protocol or not. There are two protocols currently supported now: `dutp` for dUTP-based protocols and `non_specific` for non-strand-specific protocols.
6. `annotation_refflat`: contains the path to an annotation refFlat file of the entire genome

While optional settings are:

1. `annotation_gtf`: contains path to an annotation GTF file, only required when `expression_measures` contain `fragments_per_gene`, `cufflinks_strict`, and/or `cufflinks_guided`.
2. `annotation_bed`: contains path to a flattened BED file (no overlaps), only required when `expression_measures` contain `bases_per_gene` and/or `bases_per_exon`.
3. `remove_ribosomal_reads`: whether to remove reads mapping to ribosomal genes or not, defaults to `false`.
4. `ribosomal_refflat`: contains path to a refFlat file of ribosomal gene coordinates, required when `remove_ribosomal_reads` is `true`.
5. `call_variants`: whether to call variants on the RNA-seq data or not, defaults to `false`.

In addition to these, you must also remember to supply the alignment index required by your aligner of choice. For `tophat` this is `bowtie_index`, while for `gsnap` it is `db` and `dir`.

Thus, an example settings configuration is as follows:

~~~
{
  "output_dir": "/path/to/output/dir",
  "expression_measures": ["fragments_per_gene", "bases_per_gene"],
  "strand_protocol": "dutp",
  "reference": "/path/to/reference",
  "annotation_gtf": "/path/to/gtf",
  "annotation_refflat": "/path/to/refflat",
  "gsnap": {
    "dir": "/path/to/gsnap/db/dir",
    "db": "gsnap_db_name"
  }
}
~~~

## Running Gentrap

As with other pipelines in the Biopet suite, Gentrap can be run by specifying the pipeline after the `pipeline` subcommand:

~~~
java -jar </path/to/biopet.jar> pipeline gentrap -config </path/to/config.json> -qsub -jobParaEnv BWA -run
~~~

If you already have the `biopet` environment module loaded, you can also simply call `biopet`:

~~~
biopet pipeline gentrap -config </path/to/config.json> -qsub -jobParaEnv BWA -run
~~~

It is also a good idea to specify retries (we recomend `-retry 3` up to `-retry 5`) so that cluster glitches do not interfere with your pipeline runs.

## Output Files

The number and types of output files depend on your run configuration. What you can always expect, however, is that there will be a summary JSON file of your run called `gentrap.summary.json` and a PDF report in a `report` folder called `gentrap_report.pdf`. The summary file contains files and statistics specific to the current run, which is meant for cases when you wish to do further processing with your Gentrap run (for example, plotting some figures), while the PDF report provides a quick overview of your run results.

## Getting Help

If you have any questions on running Gentrap, suggestions on how to improve the overall flow, or requests for your favorite RNA-seq related program to be added, feel free to post an issue to our issue tracker at [https://git.lumc.nl/biopet/biopet/issues](https://git.lumc.nl/biopet/biopet/issues).

