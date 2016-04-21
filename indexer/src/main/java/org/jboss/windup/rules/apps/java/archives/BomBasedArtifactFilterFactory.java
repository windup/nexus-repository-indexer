package org.jboss.windup.rules.apps.java.archives;

import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

/**
 * Creates an ArtifactFilter which accepts any artifact specified in given BOM.
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class BomBasedArtifactFilterFactory
{
    private static final Logger LOG = Logger.getLogger( BomBasedArtifactFilterFactory.class.getName() );

    // G:A:V[:C[:P]]
    // Maven uses G:A[:P[:C]]:V"
    //public static final Pattern REGEX_GAVCP = Pattern.compile("([-.\\w]+):([-.\\w]+):([-.\\w]+)(:[-.\\w]+)?(:[-.\\w]+)?");
    public static final Pattern REGEX_GAVCP = Pattern.compile("([^: ]+):([^: ]+):([^: ]+)(:[^: ]+)?(:[^: ]+)?");

    private final ArtifactDownloader downloader = new ArtifactDownloader();

    public ArtifactFilter createArtifactFilterFromBom(String coords)
    {
        Matcher mat = REGEX_GAVCP.matcher(coords);
        if (!mat.matches())
            throw new IllegalArgumentException("Wrong Maven coordinates format, must be G:A:V[:C[:P]] . " + coords);
        if (mat.groupCount() != 3 && (!StringUtils.isBlank(mat.group(4)) || !StringUtils.isBlank(mat.group(5))) )
            throw new IllegalArgumentException("Classifier and packaging is not supported for BOM, invalid: " + coords + " " + mat.groupCount());

        return createArtifactFilterFromBom(mat.group(1), mat.group(2), mat.group(3));
    }

    public ArtifactFilter createArtifactFilterFromBom(String groupId, String artifactId, String version)
    {

        final DefaultArtifact bom = new DefaultArtifact(groupId, artifactId, "pom", version);
        LOG.info("Resolving BOM: " + bom);
        Artifact artifact = downloader.downloadArtifact(bom);

        LOG.info("Getting dependencies for BOM: " + bom);
        List<Dependency> deps = downloader.getDependenciesFor(artifact, true);

        DefinitionArtifactFilter filter = new DefinitionArtifactFilter();
        for (Dependency dep : deps)
        {
            final Artifact art = dep.getArtifact();
            filter.addArtifact(art.getGroupId(), art.getArtifactId(), art.getVersion());
        }
        return filter;
    }

}
