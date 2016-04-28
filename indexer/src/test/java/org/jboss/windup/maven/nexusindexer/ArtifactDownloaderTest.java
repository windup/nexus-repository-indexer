package org.jboss.windup.maven.nexusindexer;

import org.jboss.windup.maven.nexusindexer.ArtifactDownloader;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Assume;


/**
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class ArtifactDownloaderTest
{

    public ArtifactDownloaderTest()
    {
    }

    @Test
    public void testDownloadArtifact()
    {
        System.out.println("downloadArtifact");
        // http://repository.jboss.org/nexus/content/groups/public/org/jboss/jboss-parent/19/jboss-parent-19.pom
        Artifact queryArtifact = new DefaultArtifact("org.jboss:jboss-parent:pom:19");
        ArtifactDownloader downloader = new ArtifactDownloader();
        Artifact resultingArtifact = downloader.downloadArtifact(queryArtifact);
        assertTrue("Artifact downloaded: " + queryArtifact, resultingArtifact.getFile().exists());
    }


    @Test
    public void testGetDefaultRepositories()
    {
        System.out.println("getDefaultRepositories");
        ArtifactDownloader instance = new ArtifactDownloader();
        List<RemoteRepository> result = instance.getDefaultRepositories();
        assertNotNull(result);
    }


    @Test
    public void testGetDependenciesFor()
    {
        System.out.println("getDependenciesFor");
        ArtifactDownloader instance = new ArtifactDownloader();

        {
            // http://repository.jboss.org/nexus/content/groups/public/org/jboss/bom/jboss-javaee-6.0-with-all/1.0.7.Final/jboss-javaee-6.0-with-all-1.0.7.Final.pom
            Artifact bomJee6 = new DefaultArtifact("org.jboss.bom:jboss-javaee-6.0-with-all:pom:1.0.7.Final");

            // Transitive
            Artifact bomDeltaSpike = new DefaultArtifact("org.jboss.bom:jboss-javaee-6.0-with-deltaspike:pom:1.0.7.Final");

            Artifact dependencyNotExpected = new DefaultArtifact("org.jboss.bom:jboss-javaee-6.0-with-deltaspike:pom:1.0.6.Final");
            Artifact dependencyExpected = new DefaultArtifact("org.apache.deltaspike.core:deltaspike-core-api:jar:0.4");
            Assume.assumeTrue(dependencyExpected.equals( new DefaultArtifact("org.apache.deltaspike.core:deltaspike-core-api:jar:0.4") ));

            // The other imported BOMs are actually expanded
            List<Dependency> depsResult = instance.getDependenciesFor(bomJee6, true);
            assertFalse("BOM not contains expected managed dep: " + depsResult, contains(depsResult, bomDeltaSpike));
            assertFalse("BOM not contains unexpected managed dep: " + depsResult, contains(depsResult, dependencyNotExpected));
            assertTrue("BOM contains expected managed dep: " + depsResult, contains(depsResult, dependencyExpected));

            // Direct
            depsResult = instance.getDependenciesFor(bomDeltaSpike, true);
            assertFalse("BOM DeltaSpike not contains itself: " + depsResult, contains(depsResult, bomDeltaSpike));
            assertFalse("BOM DeltaSpike not contains unexpected managed dep: " + depsResult, contains(depsResult, dependencyNotExpected));
            assertTrue("BOM DeltaSpike contains expected managed dep: " + depsResult, contains(depsResult, dependencyExpected));
        }

        {
            Artifact depEl = new DefaultArtifact("org.jboss.el:jboss-el:jar:2.0.2.CR1");
            Artifact depApi = new DefaultArtifact("javax.el:el-api:jar:1.0");
            List<Dependency> depsResult = instance.getDependenciesFor(depEl, false);
            assertTrue("Artifact contains expected normal dep", contains(depsResult, depApi));
        }
    }

    boolean contains(List<Dependency> deps, Artifact dep)
    {
        for (Dependency dep1 : deps)
        {
            if(!dep1.getArtifact().getGroupId().equals(dep.getGroupId()))
                continue;
            if(!dep1.getArtifact().getArtifactId().equals(dep.getArtifactId()))
                continue;
            if(!dep1.getArtifact().getVersion().equals(dep.getVersion()))
                continue;
            if(!dep1.getArtifact().getClassifier().equals(dep.getClassifier()))
                continue;
            if(!dep1.getArtifact().getExtension().equals(dep.getExtension()))
                continue;
            return true;
        }
        return false;
    }
}
