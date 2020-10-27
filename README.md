# Maven Central Index

Since [OSSRH-60950](https://issues.sonatype.org/browse/OSSRH-60950), `nexus-repository-indexer` provides a way to build a fixed version of the `nexus-maven-repository-index.gz` Maven Central Index (ref. [Central Index](https://maven.apache.org/repository/central-index.html#central-index)) without `module` and `pom.512` files but with the right JAR files inside.  
The `nexus-maven-repository-index.gz` Maven Central Index can either be downloaded from nightly build or built locally.  
Check the next paragraphs for both the options.

## Download fixed Maven Central Index  

There's a night build that creates ever day an update version of the `nexus-maven-repository-index.gz` Maven Central Index.  
From the ["Create fixed Maven Central Index" runs list](https://github.com/windup/nexus-repository-indexer/actions?query=event%3Aschedule+is%3Asuccess+workflow%3A%22Create+fixed+Maven+Central+Index%22) select the latest run.  
In the details page download the `nexus-maven-repository-index.gz` from the `Artifacts` section.

## Create a fixed Maven Central Index  

To build the index locally on your host, follow the following steps:

1. clone this repo  
`$ git clone https://github.com/windup/nexus-repository-indexer.git`
1. move into the `nexus-repository-indexer` folder  
`$ cd nexus-repository-indexer`
1. install the parent POM  
`$ mvn -N install`
1. install the `indexer` module  
`$ mvn -f indexer/pom.xml install`
1. build the index  
`$ mvn -f data-text/pom.xml package -DskipTests -P update-central-index`
