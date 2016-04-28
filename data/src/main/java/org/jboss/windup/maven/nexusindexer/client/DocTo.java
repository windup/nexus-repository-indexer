package org.jboss.windup.maven.nexusindexer.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.windup.maven.nexusindexer.LuceneIndexArtifactVisitor;
import org.jboss.windup.maven.nexusindexer.PackageLuceneIndexArtifactVisitor;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.Fields.ARTIFACT_ID;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.Fields.CLASSIFIER;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.Fields.GROUP_ID;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.Fields.PACKAGING;
import static org.jboss.windup.maven.nexusindexer.client.DocTo.Fields.VERSION;

/**
 * API for Lucene {@Document} to arbitrary object conversion.
 *
 * Also defines conversion for entities stored within the index created by this project.
 * Also provides the field names constants used in the documents in the created indexes.
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public interface DocTo<TargetType>
{
    TargetType convert(Document doc);

    interface Fields {
        // Bring the constants here so we can cut from the dependency.
        public static final String GROUP_ID = LuceneIndexArtifactVisitor.GROUP_ID;
        public static final String ARTIFACT_ID = LuceneIndexArtifactVisitor.ARTIFACT_ID;
        public static final String PACKAGING = LuceneIndexArtifactVisitor.PACKAGING;
        public static final String CLASSIFIER = LuceneIndexArtifactVisitor.CLASSIFIER;
        public static final String VERSION = LuceneIndexArtifactVisitor.VERSION;

        public static final String SHA1 = LuceneIndexArtifactVisitor.SHA1;

        public static final String PACKAGE = PackageLuceneIndexArtifactVisitor.FIELD_PACKAGE; // No better place for now.
        public static final String COORD_GAVCP = PackageLuceneIndexArtifactVisitor.FIELD_COORD; // No better place for now.
    }

    public static DocTo<Artifact> ARTIFACT = new DocTo<Artifact>()
    {
        public Artifact convert(Document doc)
        {
            return new DefaultArtifact(doc.get(GROUP_ID), doc.get(ARTIFACT_ID), doc.get(CLASSIFIER), doc.get(PACKAGING), doc.get(VERSION));
        }
    };

    public static DocTo<String> COORD_GAVCP_TAKEOVER = new DocTo<String>()
    {
        public String convert(Document doc)
        {
            return doc.get(Fields.COORD_GAVCP);
        }
    };

    public static final Pattern REGEX_GAVCP = Pattern.compile("([^: ]+):([^: ]+):([^: ]+):([^: ]+):([^: ]+)");
    
    public static DefaultArtifact fromGAVPC(String coordGacev)
    {
        Matcher mat = REGEX_GAVCP.matcher(coordGacev);
        if (!mat.matches())
            throw new IllegalArgumentException("Wrong Maven coordinates format, must be G:A:V[:C[:P]] . " + coordGacev);
        return new DefaultArtifact(mat.group(1), mat.group(2), mat.group(3), mat.group(4), mat.group(5));
    }

    public static DocTo<String> COORD_GACEV = new DocTo<String>()
    {
        public String convert(Document doc)
        {
            return new StringBuilder(
                        doc.get(GROUP_ID)).append(':')
                .append(doc.get(ARTIFACT_ID)).append(':')
                .append(doc.get(CLASSIFIER)).append(':')
                .append(doc.get(PACKAGING)).append(':')
                .append(doc.get(VERSION)).toString();
        }
    };


}
