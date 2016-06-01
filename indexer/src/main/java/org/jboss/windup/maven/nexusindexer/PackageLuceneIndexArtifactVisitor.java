package org.jboss.windup.maven.nexusindexer;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class PackageLuceneIndexArtifactVisitor extends LuceneIndexArtifactVisitor
{
    private static final Logger LOG = Logger.getLogger(PackageLuceneIndexArtifactVisitor.class.getName());
    public static final String PACKAGE_INDEX_DIR_MARKER = "package-archive-map.lucene.marker";
    public static final String FIELD_COORD = "coord";
    public static final String FIELD_PACKAGE = "package";

    private ArtifactDownloader downloader = new ArtifactDownloader();


    public PackageLuceneIndexArtifactVisitor(File outputDir, ArtifactFilter filter)
    {
        super(outputDir, filter);
    }

    public static String getLuceneIndexDirMarkerFileName()
    {
        return PACKAGE_INDEX_DIR_MARKER;
    }

    /**
     * Not quite sure if this should create one document per coords + packages
     * or 1 document per package.
     */
    @Override
    protected Iterable<Document> artifactToDocs(ArtifactInfo a)
    {
        // Download and scan the packages.
        // Alterntively, I should be able to get this information from the index, but I don't know how.
        // http://blog.sonatype.com/2009/06/nexus-indexer-api-part-1/
        // http://blog.sonatype.com/2009/06/nexus-indexer-api-part-2/
        final DefaultArtifact artToDownload = new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getPackaging(), a.getVersion());
        LOG.info("Resolving and scanning: " + artToDownload);
        final Artifact artifact = downloader.downloadArtifact(artToDownload);

        final List<Document> docs = new ArrayList<>(64); // Guesstimated # of packages in a jar.
        try
        {
            final Document doc = new Document();
            final String coord = toCoordGAVPC(artifact);
            doc.add(new StringField(FIELD_COORD, coord, Field.Store.YES));
            LOG.info(" ---- Created a doc for " + coord);
            ZipUtil.scanClassesInJar(artifact.getFile().toPath(), true, new ZipUtil.Visitor<String>()
            {
                public void visit(String pkg)
                {
                    doc.add(new StringField(FIELD_PACKAGE, pkg, Field.Store.YES));
                    LOG.info("     ----- Storing: " + pkg);
                }
            });
            docs.add(doc);
        }
        catch (IOException ex)
        {
            LOG.warning("Error scanning JAR artifact: " + artifact + "\n    " + ex.getMessage());
        }
        return docs;
    }


    public static String toCoordGAVPC(Artifact artifact)
    {
        return new StringBuilder(
                        artifact.getGroupId()).append(':')
                .append(artifact.getArtifactId()).append(':')
                .append(artifact.getVersion()).append(':')
                .append(artifact.getExtension()).append(':')
                .append(artifact.getClassifier())
                .toString();
    }

}
