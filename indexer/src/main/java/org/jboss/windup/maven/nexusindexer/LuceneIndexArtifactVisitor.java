package org.jboss.windup.maven.nexusindexer;


import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.apache.maven.index.ArtifactInfo;

/**
 * For each visited archive, this creates a Lucene document with these fields:
 *  sha1 groupId artifactId packaging classifier version
 *
 * @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class LuceneIndexArtifactVisitor implements RepositoryIndexManager.ArtifactVisitor<Object>
{
    private static final Logger LOG = Logger.getLogger(LuceneIndexArtifactVisitor.class.getName());

    public static final String ARCHIVE_METADATA_INDEX_DIR_MARKER = "archive-metadata.lucene.marker";

    private final ArtifactFilter filter;
    private final File indexDir;
    private final IndexWriter indexWriter;
    private SimpleFSDirectory luceneOutputDirResource;


    public LuceneIndexArtifactVisitor(File outputDir, ArtifactFilter filter)
    {
        try
        {
            this.filter = filter;
            this.indexDir = outputDir;
            this.indexDir.mkdirs();
            File markerFile = new File(indexDir, getLuceneIndexDirMarkerFileName());
            FileUtils.write(markerFile, "This file is searched by Windup to locate the Lucene index with repository metadata.");

            // Create our local result index.
            this.luceneOutputDirResource = new SimpleFSDirectory(indexDir);
            StandardAnalyzer standardAnalyzer = new StandardAnalyzer(Version.LUCENE_48);
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_48, standardAnalyzer);
            this.indexWriter = new IndexWriter(luceneOutputDirResource, config);
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed creating Lucene index writer in: " + outputDir + "\n    " + ex.getMessage(), ex);
        }
    }


    public static String getLuceneIndexDirMarkerFileName()
    {
        return ARCHIVE_METADATA_INDEX_DIR_MARKER;
    }


    @Override
    public void visit(ArtifactInfo artifact)
    {
        if (!this.filter.accept(artifact))
            return;
        try
        {
            // Add to Lucene index
            indexWriter.addDocuments(artifactToDocs(artifact));
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed writing to IndexWriter: " + ex.getMessage(), ex);
        }
    }


    protected Iterable<Document> artifactToDocs(ArtifactInfo artifact)
    {
        Document outputDoc = new Document();
        outputDoc.add(new StringField("sha1", artifact.getSha1(), Field.Store.YES));
        outputDoc.add(new StringField("groupId", artifact.getGroupId(), Field.Store.YES));
        outputDoc.add(new StringField("artifactId", artifact.getArtifactId(), Field.Store.YES));
        outputDoc.add(new StringField("packaging", artifact.getPackaging(), Field.Store.YES));
        outputDoc.add(new StringField("classifier", artifact.getClassifier(), Field.Store.YES));
        outputDoc.add(new StringField("version", artifact.getVersion(), Field.Store.YES));
        return Collections.singleton(outputDoc);
    }


    public Object done()
    {
        try
        {
            this.indexWriter.close();
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed closing Lucene index writer in: " + indexDir + "\n    " + ex.getMessage(), ex);
        }
        finally
        {
            this.luceneOutputDirResource.close();
        }
        return null;
    }

}
