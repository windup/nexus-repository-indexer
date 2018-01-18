package org.jboss.windup.maven.nexusindexer.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.SimpleFSDirectory;
import org.jboss.windup.maven.nexusindexer.ArtifactFilter;

/**
 * @author <a href="mailto:jesse.sightler@gmail.com">Jesse Sightler</a>
 */
public class TextFileToLucene7Converter
{
    private static Logger LOG = Logger.getLogger(TextFileToLucene7Converter.class.getCanonicalName());

    public static final String ARCHIVE_METADATA_INDEX_DIR_MARKER = "archive-metadata.lucene.marker";
    public static final String SHA1 = "sha1";
    public static final String GROUP_ID = "groupId";
    public static final String ARTIFACT_ID = "artifactId";
    public static final String PACKAGING = "packaging";
    public static final String CLASSIFIER = "classifier";
    public static final String VERSION = "version";
    private final ArtifactFilter filter;
    private final File indexDir;
    private final IndexWriter indexWriter;
    private SimpleFSDirectory luceneOutputDirResource;

    public static void main(String[] args) throws Exception
    {
        if (args.length < 2)
            printUsage();

        String inputFileStr = args[0];
        String outputDirStr = args[1];

        File inputFile = new File(inputFileStr);
        File outputDir = new File(outputDirStr);

        File[] childFiles = inputFile.listFiles();
        if (childFiles == null)
            throw new RuntimeException("No files in input directory: " + inputFile);

        TextFileToLucene7Converter converter = new TextFileToLucene7Converter(outputDir, ArtifactFilter.LIBRARIES);
        for (int i = 0; i < childFiles.length; i++)
        {
            File childFile = childFiles[i];
            LOG.info("Indexing: " + childFile);
            converter.convert(childFile);
        }
        converter.done();
    }

    private static void printUsage()
    {
        System.err.println("  Usage:");
        System.err.println("    java -jar ... <inputDirectory> <indexDirectory>");
        System.err.println("");
        System.err.println("  Parameters:");
        System.err.println("    <inputDirectory>   Where to put the created mapping files.");
        System.err.println("    <indexDirectory>   Where to store the repository index data files.");
    }

    public TextFileToLucene7Converter(File outputDir, ArtifactFilter filter)
    {
        try
        {
            this.filter = filter;
            this.indexDir = outputDir;
            this.indexDir.mkdirs();
            File markerFile = new File(indexDir, getLuceneIndexDirMarkerFileName());
            FileUtils.write(markerFile, "This file is searched by Windup to locate the Lucene index with repository metadata.");

            // Create our local result index.
            this.luceneOutputDirResource = new SimpleFSDirectory(indexDir.toPath());
            StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(standardAnalyzer);
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

    private void convert(File inputFile) throws IOException
    {
        try (FileReader fileReader = new FileReader(inputFile))
        {
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line = null;
            int lineNumber = 0;
            while ( (line = bufferedReader.readLine()) != null)
            {
                lineNumber++;

                if (line.startsWith("#") || line.trim().isEmpty())
                    continue;
                String[] parts = StringUtils.split(line, ' ');
                if (parts.length < 2)
                    throw new IllegalArgumentException("Expected 'SHA1 GROUP_ID:ARTIFACT_ID:[PACKAGING:[COORDINATE:]]VERSION', but was: [" + line
                            + "] in [" + inputFile + "] at line [" + lineNumber + "]");

                String sha1 = line.substring(0, line.indexOf(" "));
                String gav = line.substring(line.indexOf(" ") + 1);

                String[] gavArray = gav.split(":");
                String groupId = StringUtils.defaultString(gavArray[0], "");
                String artifactId = StringUtils.defaultString(gavArray[1], "");
                String packaging = StringUtils.defaultString(gavArray[2], "");
                String coordinate = StringUtils.defaultString(gavArray[3], "");
                String version = StringUtils.defaultString(gavArray[4], "");

                visit(sha1, groupId, artifactId, version, packaging, coordinate);
            }
        }
    }

    public void visit(String sha1, String group, String artifactId, String version, String packaging, String classifier)
    {
        if (!this.filter.accept(sha1, group, artifactId, version, packaging, classifier))
            return;
        try
        {
            // Add to Lucene index
            indexWriter.addDocuments(artifactToDocs(sha1, group, artifactId, version, packaging, classifier));
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed writing to IndexWriter: " + ex.getMessage(), ex);
        }
    }

    protected Iterable<Document> artifactToDocs(String sha1, String group, String artifactId, String version, String packaging, String classifier)
    {
        Document outputDoc = new Document();
        outputDoc.add(new StringField(SHA1, sha1, Field.Store.YES));
        outputDoc.add(new StringField(GROUP_ID, group, Field.Store.YES));
        outputDoc.add(new StringField(ARTIFACT_ID, artifactId, Field.Store.YES));
        outputDoc.add(new StringField(PACKAGING, packaging, Field.Store.YES));
        outputDoc.add(new StringField(CLASSIFIER, classifier, Field.Store.YES));
        outputDoc.add(new StringField(VERSION, version, Field.Store.YES));
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
            try
            {
                this.luceneOutputDirResource.close();
            }
            catch (IOException ex)
            {
                throw new RuntimeException("Failed closing Lucene index writer in: " + indexDir + "\n    " + ex.getMessage(), ex);
            }
        }
        return null;
    }

}
