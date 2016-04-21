package org.jboss.windup.rules.apps.java.archives;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.apache.maven.index.ArtifactInfo;

/**
 * Filters Maven artifacts based on direct definitions.
 * TODO: Support classifier and type.
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class DefinitionArtifactFilter implements ArtifactFilter
{
    private static final Logger LOG = Logger.getLogger( DefinitionArtifactFilter.class.getName() );

    private static final String ANY_MATCHES = null; //"org.jboss.nexusIndexer.ANYTHING_MATCHES";

    /**
     * A special structure:
     * First level is for groupId, second for artifactId, third for version.
     * A presence of ANY_MATCHES_* values means it will match any value at that level.
     * So the user can define e.g. org.jboss:*:* or org.jboss.windup:windup-core:*.
     */
    private final Map<String, Map<String, Set<String>>> gavTree = new HashMap();

    public DefinitionArtifactFilter addArtifact(String groupId, String artifactId, String version)
    {
        // groupId
        Map<String, Set<String>> artifactToVersions = this.gavTree.get(groupId);
        if (artifactToVersions == null)
            this.gavTree.put(groupId, artifactToVersions = new HashMap());

        // artifactId
        Set<String> versions = artifactToVersions.get(artifactId);
        if (versions == null)
            artifactToVersions.put(artifactId, versions = new HashSet<>());

        versions.add(version);

        return this;
    }


    @Override
    public boolean accept(ArtifactInfo artifact)
    {
        Map<String, Set<String>> artifactIdToVersions = this.gavTree.get(artifact.getGroupId());
        if (artifactIdToVersions == null)
            return false;

        // Any artifactId
        Set<String> anyArtifactId = artifactIdToVersions.get(ANY_MATCHES);
        if (anyArtifactId != null){
            if(anyArtifactId.contains(ANY_MATCHES))
                return true; // foo:*:* definition found
            if(anyArtifactId.contains(artifact.getVersion()))
                return true; // foo:*:1.0 definition found
        }

        // Given artifactId
        Set<String> givenArtifactId = artifactIdToVersions.get(artifact.getArtifactId());
        if (givenArtifactId == null)
            return false; // No matching G:A.

        if(givenArtifactId.contains(ANY_MATCHES))
            return true;  // foo:bar:* definition found
        if(givenArtifactId.contains(artifact.getVersion()))
            return true;  // foo:bar:1.0 definition found

        return false;     // No matching G:A:V.
    }

}
