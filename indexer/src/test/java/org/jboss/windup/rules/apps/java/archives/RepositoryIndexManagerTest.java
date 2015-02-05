package org.jboss.windup.rules.apps.java.archives;

import java.io.File;

import org.jboss.forge.addon.dependencies.DependencyRepository;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
@Ignore
public class RepositoryIndexManagerTest
{
    private final File dataDir = new File("target/");
    private final DependencyRepository repository = new DependencyRepository("central",
                "http://repo1.maven.org/maven2");

    @Test
    public void testGeneateMetadataFile() throws Exception
    {
        RepositoryIndexManager.generateMetadata(repository, dataDir);
    }
}
