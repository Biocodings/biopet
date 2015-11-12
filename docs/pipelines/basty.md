# Basty

## Introduction


Basty is a pipeline for aligning bacterial genomes and detecting structural variations on the level of SNPs.
Basty will output phylogenetic trees, which makes it very easy to look at the variations between certain species or strains.

### Tools for this pipeline
* [Shiva](shiva.md)
* [BastyGenerateFasta](../tools/BastyGenerateFasta.md)
* <a href="http://sco.h-its.org/exelixis/software.html" target="_blank">RAxml</a>
* <a href="https://github.com/sanger-pathogens/Gubbins" target="_blank">Gubbins</a>

### Requirements

To run with a specific species, please do not forget to create the proper index files.
The index files are created from the supplied reference:

* ```.dict``` (can be produced with <a href="http://broadinstitute.github.io/picard/" target="_blank">Picard tool suite</a>)
* ```.fai``` (can be produced with <a href="http://samtools.sourceforge.net/samtools.shtml" target="_blank">Samtools faidx</a> 
* ```.idxSpecificForAligner``` (depending on which aligner is used one should create a suitable index specific for that aligner. 
Each aligner has his own way of creating index files. Therefore the options for creating the index files can be found inside the aligner itself)

### Configuration
To run Basty, please create the proper [Config](../general/config.md) files.

Batsy uses the [Shiva](shiva.md) pipeline internally. Please check the documentation for this pipeline for the options.

#### Required configuration values

| Submodule | Name | Type | Default | Function |
| --------- | ---- | ---- | ------- | -------- |
| shiva | variantcallers | List[String] |  | Which variant caller to use |
| - | output_dir | Path | Path to output directory |


#### Other options

Specific configuration options additional to Basty are:

| Submodule | Name | Type | Default | Function |
| --------- | ---- | ---- | ------- | -------- |
| raxml | seed | Integer | 12345 | RAxML Random seed|
| raxml | raxml_ml_model | String | GTRGAMMAX | RAxML model |
| raxml | ml_runs | Integer | 20 | Number of RaxML runs |
| raxml | boot_runs | Integer | 100 | Number of RaxML boot runs |


#### Example settings config

```json

{
    "output_dir": </path/to/out_directory>,
    "shiva": {
        "variantcallers": ["freeBayes"]
    },
    "raxml" : {
        "ml_runs": 50
    }
}

```

### Example

##### For the help screen:
~~~
biopet pipeline basty -h
~~~

##### Run the pipeline:
Note that one should first create the appropriate [configs](../general/config.md).

~~~
biopet pipeline basty -run -config MySamples.json -config MySettings.json
~~~

### Result files
The output files this pipeline produces are:

* A complete output from [Flexiprep](flexiprep.md)
* BAM files, produced with the mapping pipeline. (either BWA, Bowtie, Stampy, Star and Star 2-pass. default: BWA)
* VCF file from all samples together 
* The output from the tool [BastyGenerateFasta](../tools/BastyGenerateFasta.md)
    * FASTA containing variants only
    * FASTA containing all the consensus sequences based on min. coverage (default:8) but can be modified in the config
* A phylogenetic tree based on the variants called with the Shiva pipeline generated with the tool [BastyGenerateFasta](../tools/BastyGenerateFasta.md)


~~~
.
├── fastas
│   ├── consensus.fasta
│   ├── consensus.snps_only.fasta
│   ├── consensus.variant.fasta
│   ├── consensus.variant.snps_only.fasta
│   ├── variant.fasta
│   ├── variant.fasta.reduced
│   ├── variant.snps_only.fasta
│   └── variant.snps_only.fasta.reduced
│
├── reference
│   ├── reference.consensus.fasta
│   ├── reference.consensus.snps_only.fasta
│   ├── reference.consensus_variants.fasta
│   ├── reference.consensus_variants.snps_only.fasta
│   ├── reference.variants.fasta
│   └── reference.variants.snps_only.fasta
│
├── samples
│   ├── 078NET024
│   │   ├── 078NET024.consensus.fasta
│   │   ├── 078NET024.consensus.snps_only.fasta
│   │   ├── 078NET024.consensus_variants.fasta
│   │   ├── 078NET024.consensus_variants.snps_only.fasta
│   │   ├── 078NET024.variants.fasta
│   │   ├── 078NET024.variants.snps_only.fasta
│   │   ├── run_8080_2
│   │   └── variantcalling
│   ├── 078NET025
│       ├── 078NET025.consensus.fasta
│       ├── 078NET025.consensus.snps_only.fasta
│       ├── 078NET025.consensus_variants.fasta
│       ├── 078NET025.consensus_variants.snps_only.fasta
│       ├── 078NET025.variants.fasta
│       ├── 078NET025.variants.snps_only.fasta
│       ├── run_8080_2
│       └── variantcalling
│
├── trees
│   ├── snps_indels
│   │   ├── boot_list
│   │   ├── gubbins
│   │   └── raxml
│   └── snps_only
│       ├── boot_list
│       ├── gubbins
│       └── raxml
└── variantcalling
    ├── multisample.final.vcf.gz
    ├── multisample.final.vcf.gz.tbi
    ├── multisample.raw.variants_only.vcf.gz.tbi
    ├── multisample.raw.vcf.gz
    ├── multisample.raw.vcf.gz.tbi
    ├── multisample.ug.discovery.variants_only.vcf.gz.tbi
    ├── multisample.ug.discovery.vcf.gz
    └── multisample.ug.discovery.vcf.gz.tbi
~~~
### Best practice


## References

## Getting Help

If you have any questions on running Basty, suggestions on how to improve the overall flow, or requests for your favorite 
SNP typing algorithm, feel free to post an issue to our issue tracker at [GitHub](https://github.com/biopet/biopet). Or contact us directly via: [SASC email](mailto:SASC@lumc.nl)
