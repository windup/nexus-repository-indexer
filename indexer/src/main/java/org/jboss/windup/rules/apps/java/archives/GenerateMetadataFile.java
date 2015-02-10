package org.jboss.windup.rules.apps.java.archives;

import java.io.File;
import java.util.logging.Logger;

import org.jboss.forge.addon.dependencies.DependencyRepository;

/**
 * Provides a main class for generating Maven Nexus metadata files.
 *
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * @author Ondrej Zizka, ozizka at redhat.com
 */
public class GenerateMetadataFile
{
    private static Logger log = Logger.getLogger(GenerateMetadataFile.class.getName());

    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
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
            RepositoryIndexManager.generateMetadata(repository, indexDir, outputDir);
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
