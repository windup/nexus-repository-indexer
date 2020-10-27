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

        log.info(String.format("Generating index file: [%s]", outputDir));
        RepositoryIndexManager.updateNexusIndex(repository, indexDir, outputDir);
    }


    private static void printUsage()
    {
        System.err.println("  Usage:");
        System.err.println("    java -jar ... <repoId> <repoUrl> <outputDirectory> [<indexDirectory>]");
        System.err.println("");
        System.err.println("  Parameters:");
        System.err.println("    <repoId>           ID of the repository; used for generated file names.");
        System.err.println("    <repoUrl>          URL of the repository.");
        System.err.println("    <outputDirectory>  Where to put the created index.");
        System.err.println("    <indexDirectory>   Where to store the temporary Lucene index data files.");
    }
}
