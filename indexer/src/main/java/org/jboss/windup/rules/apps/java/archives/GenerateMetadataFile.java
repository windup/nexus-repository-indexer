package org.jboss.windup.rules.apps.java.archives;

import java.io.File;
import java.util.logging.Logger;

import org.jboss.forge.addon.dependencies.DependencyRepository;

/**
 * Provides a main class for generating Maven Nexus metadata files.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class GenerateMetadataFile
{
    private static Logger log = Logger.getLogger(GenerateMetadataFile.class.getName());

    public static void main(String[] args) throws Exception
    {
        String repositoryId = args[0];
        String repositoryUrl = args[1];
        String outputDirectory = args[2];

        File outputDir = new File(outputDirectory);
        DependencyRepository repository = new DependencyRepository(repositoryId, repositoryUrl);

        if (!RepositoryIndexManager.metadataExists(repository, outputDir))
        {
            log.info("Generating metadata file: [" + RepositoryIndexManager.getMetadataFile(repository, outputDir) + "]");
            RepositoryIndexManager.generateMetadata(repository, outputDir);
        }
        else
        {
            log.info("Metadata file already exists, not generating: [" + RepositoryIndexManager.getMetadataFile(repository, outputDir) + "]");
        }
    }
}
