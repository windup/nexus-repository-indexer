package org.jboss.windup.maven.nexusindexer;

import org.jboss.forge.addon.dependencies.DependencyRepository;

import java.io.File;
import java.util.logging.Logger;

/**
 * Provides a main class for updating (and fixing) Maven Nexus Index.
 *
 * @author Marco Rizzi, mrizzi at redhat.com
 */
public class UpdateNexusIndex
{
    private static Logger log = Logger.getLogger(UpdateNexusIndex.class.getName());

    public static void main(String[] args) throws Exception
    {
        if (args.length < 4)
            printUsage();

        String repositoryId = args[0];
        String repositoryUrl = args[1];
        String outputDirStr = args[2];
        String indexDirStr = args.length >= 4 ? args[3] : outputDirStr;


        File outputDir = new File(outputDirStr);
        File indexDir  = new File(indexDirStr);

        DependencyRepository repository = new DependencyRepository(repositoryId, repositoryUrl);

        if (!RepositoryIndexManager.metadataExists(repository, outputDir))
        {
            log.info("Generating metadata file: [" + RepositoryIndexManager.getMetadataFile(repository, outputDir) + "]");
            RepositoryIndexManager.updateNexusIndex(repository, indexDir, outputDir);
        }
        else
        {
            log.info("Metadata file already exists, not generating: [" + RepositoryIndexManager.getMetadataFile(repository, outputDir) + "]");
        }
    }


    private static void printUsage()
    {
        System.err.println("  Usage:");
        System.err.println("    java -jar ... <repoId> <repoUrl> <outputDirectory> [<indexDirectory>]");
        System.err.println("");
        System.err.println("  Parameters:");
        System.err.println("    <repoId>           ID of the repository; used for generated file names.");
        System.err.println("    <repoUrl>          URL of the repository.");
        System.err.println("    <outputDirectory>  Where to put the created mapping files.");
        System.err.println("    <indexDirectory>   Where to store the repository index data files.");
    }
}
