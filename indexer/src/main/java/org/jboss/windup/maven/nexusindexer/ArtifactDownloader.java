package org.jboss.windup.maven.nexusindexer;


import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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

/**
 * Downloads Maven artifacts.
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class ArtifactDownloader
{

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

}
