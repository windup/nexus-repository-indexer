package org.jboss.windup.maven.nexusindexer.client;

import java.util.regex.Matcher;
import org.eclipse.aether.artifact.DefaultArtifact;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.REGEX_GAVCP;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class IndexerUtils
{
    public static DefaultArtifact fromGAVPC(String coordGacev)
    {
        Matcher mat = REGEX_GAVCP.matcher(coordGacev);
        if (!mat.matches())
            throw new IllegalArgumentException("Wrong Maven coordinates format, must be G:A:V[:C[:P]] . " + coordGacev);
        return new DefaultArtifact(mat.group(1), mat.group(2), mat.group(3), mat.group(4), mat.group(5));
    }
}
