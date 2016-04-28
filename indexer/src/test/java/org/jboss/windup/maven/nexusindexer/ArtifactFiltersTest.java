package org.jboss.windup.maven.nexusindexer;

import org.jboss.windup.maven.nexusindexer.ArtifactFilter;
import org.jboss.windup.maven.nexusindexer.DefinitionArtifactFilter;
import org.jboss.windup.maven.nexusindexer.BomBasedArtifactFilterFactory;
import org.apache.maven.index.ArtifactInfo;
import static org.jboss.windup.maven.nexusindexer.DefinitionArtifactFilter.ANY_MATCHES;
import org.junit.Test;
import static org.junit.Assert.*;


/**
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class ArtifactFiltersTest
{
    @Test
    public void testAccept()
    {
        System.out.println("accept()");

        DefinitionArtifactFilter filter = new DefinitionArtifactFilter();
        filter
            .addArtifact("org.jboss", "jboss-parent", "19")
            .addArtifact("org.jboss", "nullString", ANY_MATCHES)
            .addArtifact("org.jboss", "emptyString", "")
            .addArtifact("cz.zizka.ondra", ANY_MATCHES, ANY_MATCHES)
            ;

        assertTrue(filter.accept(new ArtifactInfo(null, "org.jboss", "jboss-parent", "19", "", "")));
        assertTrue(filter.accept(new ArtifactInfo(null, "org.jboss", "jboss-parent", "19", "", "jar")));
        assertTrue(filter.accept(new ArtifactInfo(null, "org.jboss", "jboss-parent", "19", "", "pom")));
        assertTrue(!filter.accept(new ArtifactInfo(null, "org.jboss", "jboss-parent", "18", "", "")));
        assertTrue(!filter.accept(new ArtifactInfo(null, "org.jboss", "no-this-not", "19", "", "")));
        assertTrue(filter.accept(new ArtifactInfo(null, "org.jboss", "nullString", "19", "", "")));
        assertTrue(!filter.accept(new ArtifactInfo(null, "org.jboss", "emptyString", "19", "", "")));
        assertTrue(!filter.accept(new ArtifactInfo(null, "org.jboss", "emptyString", "19", "", "har")));
        assertTrue(filter.accept(new ArtifactInfo(null, "cz.zizka.ondra", "whatever", "1", "", "")));
    }

    @Test
    public void testBomBasedFilterFactory()
    {
        ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss.bom:jboss-javaee-6.0-with-all:1.0.7.Final");
        assertTrue(!bomFilter.accept(new ArtifactInfo(null, "org.jboss.bom", "jboss-javaee-6.0-with-deltaspike", "1.0.7.Final", null, null)));
        assertTrue(bomFilter.accept(new ArtifactInfo(null, "org.apache.deltaspike.core","deltaspike-core-api","0.4", null, null)));
    }
}
