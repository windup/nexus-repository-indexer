package org.jboss.windup.maven.nexusindexer;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoRecord;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.forge.addon.dependencies.DependencyRepository;

/**
 * Downloads Maven artifacts.
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class ArtifactDownloader
{
    private static final Logger LOG = Logger.getLogger(ArtifactDownloader.class.getName());

    private final RepositorySystem system = MavenAetherUtils.newRepositorySystem();
    private final RepositorySystemSession session = MavenAetherUtils.createSession(system, MavenAetherUtils.getWorkingRepoPath());
    private final List<RemoteRepository> repositories;

    public ArtifactDownloader()
    {
        this.repositories = this.getDefaultRepositories();
    }

    public ArtifactDownloader(RemoteRepository... repositories)
    {
        this.repositories = Arrays.asList(repositories);
    }

    /**
     * Returns a list of repositories to use for resolving artifacts,
     * currently Maven Central and JBoss Repository.
     * Override to use another list of repositories for the default constructor.
     */
    public List<RemoteRepository> getDefaultRepositories()
    {
        return new LinkedList<RemoteRepository>()
        {
            {
                add(new RemoteRepository.Builder("jboss", "default", "http://repository.jboss.org/nexus/content/groups/public/").build());
                add(new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build());
            }
        };
    }



    public Artifact downloadArtifact(Artifact artifact_)
    {
        // Download the artifact
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact_);
        artifactRequest.setRepositories(repositories);
        ArtifactResult artifactResult;
        try
        {
            artifactResult = system.resolveArtifact(session, artifactRequest);
            Artifact artifact = artifactResult.getArtifact();
            return artifact;
        }
        catch (ArtifactResolutionException ex)
        {
            throw new RuntimeException("Error resolving artifact: " + artifact_ + "\n    " + ex.getMessage(), ex);
        }
    }


    /**
     * @return Normal or managed dependencies of given artifact.
     */
    public List<Dependency> getDependenciesFor(Artifact artifact, boolean managed)
    {
        try
        {
            // Read the dependencies from the BOM's <dependencyManagement>
            // and add them to the filter.
            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact(artifact);
            descriptorRequest.setRepositories(repositories);
            ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);
            return managed ? descriptorResult.getManagedDependencies() : descriptorResult.getDependencies();
        }
        catch (ArtifactDescriptorException ex)
        {
            throw new RuntimeException(String.format("Can't resolve the BOM artifact: %s %s", artifact, ex.getMessage()), ex);
        }
    }
    public static String getJarSha1(String repositoryUrl, ArtifactInfo artifactInfo) throws IOException
    {
        final String uInfo = artifactInfo.getUinfo();
        final String[] gav = uInfo.split("\\" + ArtifactInfoRecord.FS);
        // e.g. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE-javadoc.jar.sha1
        final String sha1FileUrl = new StringBuilder(repositoryUrl)
                // groupId
                .append("/").append(gav[0].replace('.', '/'))
                // artifactId
                .append("/").append(gav[1])
                // version
                .append("/").append(gav[2])
                // file name
                .append("/").append(gav[1]).append("-").append(gav[2]).append(".jar.sha1").toString();
        final URL url = new URL(sha1FileUrl);
        final BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        // the hash sha1 file should be always a 1 line text file
        final String sha1 = in.readLine();
        in.close();
        // check the hash has the expected length
        if (!(sha1 != null && sha1.length() == 40)) {
            LOG.log(Level.WARNING, String.format("Dependency %s the retrieve hash (%s) is not valid so it will be skipped", uInfo, sha1));
            throw new IOException("Impossible to retrieve file from " + sha1FileUrl);
        }
        LOG.log(Level.FINE, String.format("Dependency %s hash is %s", uInfo, sha1));
        return sha1;
    }
}
