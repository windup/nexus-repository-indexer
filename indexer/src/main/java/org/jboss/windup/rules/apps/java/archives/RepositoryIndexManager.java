package org.jboss.windup.rules.apps.java.archives;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jboss.forge.addon.dependencies.DependencyRepository;


/**
 * Downloads Maven index from given repository and produces a list of all artifacts, using this format: "SHA G:A:V[:C]".
 *
 * @author Ondrej Zizka, ozizka at redhat.com
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class RepositoryIndexManager implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(RepositoryIndexManager.class.getName());

    // Predefined BOMs. See http://repository.jboss.org/nexus/content/groups/public/org/jboss/bom/
    private static final String JBOSS_PARENT_20 = "org.jboss:jboss-parent:20";
    private static final String BOM_EAP7_TOOLS  = "org.jboss.bom:jboss-javaee-7.0-eap-with-tools:7.0.0-SNAPSHOT";
    private static final String BOM_EAP7        = "org.jboss.bom:jboss-eap-javaee7:7.0.0-SNAPSHOT";

    public static final String LUCENE_SUBDIR_CHECKSUMS = "lucene";
    public static final String LUCENE_SUBDIR_PACKAGES = "lucene-packages";

    private final File indexDirectory;

    private final PlexusContainer plexusContainer;
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Wagon httpWagon;
    private final IndexingContext context;

    private final File localCache;
    private final File indexDir;

    /**
     * Download the index for the given {@link DependencyRepository} and store the results at the specified output {@link File}
     * directory.
     */
    public static void generateMetadata(DependencyRepository repository, File indexDir, File outputDir) throws Exception
    {
        try (RepositoryIndexManager manager = new RepositoryIndexManager(indexDir, repository))
        {
            LOG.info("Downloading or updating index into " + indexDir.getPath());
            manager.downloadIndexAndUpdate();
            LOG.info("Writing selected Nexus index data to " + outputDir.getPath());
            manager.writeMetadataTo(outputDir, repository);
        }
    }

    /**
     * Return <code>true</code> if metadata exists for the given {@link DependencyRepository} and output {@link File}
     * directory.
     */
    public static boolean metadataExists(DependencyRepository repository, File outputDir)
    {
        return getMetadataFile(repository, outputDir).exists();
    }

    /**
     * Get the metadata file for the given {@link DependencyRepository} and output {@link File} directory.
     */
    public static File getMetadataFile(DependencyRepository repository, File outputDir)
    {
        return new File(outputDir, repository.getId() + ".archive-metadata.txt");
    }

    /*
     * Make it clear that this should not be instantiated.
     */
    private RepositoryIndexManager(File indexDirectory, DependencyRepository repository) throws PlexusContainerException,
                ComponentLookupException, IOException
    {
        final boolean updateExistingIndex = true;

        this.indexDirectory = indexDirectory;

        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
        this.plexusContainer = new DefaultPlexusContainer(config);

        this.indexer = plexusContainer.lookup(Indexer.class);
        this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
        this.httpWagon = plexusContainer.lookup(Wagon.class, "http");

        this.localCache = new File(this.indexDirectory, repository.getId() + "-cache");
        this.indexDir = new File(this.indexDirectory, repository.getId() + "-index");

        /*
         * https://maven.apache.org/maven-indexer/indexer-core/apidocs/index.html
         */
        List<IndexCreator> indexers = new ArrayList<>();
        indexers.add(plexusContainer.lookup(IndexCreator.class, MinimalArtifactInfoIndexCreator.ID));
        this.context = this.indexer.createIndexingContext(
            repository.getId() + "Context", repository.getId(),
            this.localCache, this.indexDir,
            repository.getUrl(), null, true, updateExistingIndex, indexers);
    }

    private void downloadIndexAndUpdate() throws IOException
    {
        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, new LoggingTransferListener(LOG), null, null);
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(this.context, resourceFetcher);
        updateRequest.setIncrementalOnly(false);
        updateRequest.setForceFullUpdate(false);
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
        if (updateResult.isFullUpdate())
            LOG.info("Fully updated index for repository [" + this.context.getId() + "] - [" + this.context.getRepositoryUrl() + "]");
        else
            LOG.info("Incrementally updated index for repository [" + this.context.getId() + "] - [" + this.context.getRepositoryUrl() + "]");
    }


    /**
     * Passes all artifacts from the index to the visitors.
     *
     * @see org.jboss.windup.rules.apps.java.archives.config.ArchiveIdentificationConfigLoadingRuleProvider
     */
    private void writeMetadataTo(File outDir, DependencyRepository repository) throws IOException
    {
        outDir.mkdirs();

        // Maven repo index
        final IndexSearcher searcher = this.context.acquireIndexSearcher();
        final IndexReader reader = searcher.getIndexReader();
        Bits liveDocs = MultiFields.getLiveDocs(reader);


        final File textMetadataFile = getMetadataFile(repository, outDir);
        SortingLineWriterArtifactVisitor writerVisitor = new SortingLineWriterArtifactVisitor(textMetadataFile, ArtifactFilter.LIBRARIES);

        LuceneIndexArtifactVisitor basicIndexerVisitor = new LuceneIndexArtifactVisitor(new File(outDir, LUCENE_SUBDIR_CHECKSUMS), ArtifactFilter.LIBRARIES);

        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss", "jboss-parent", "19");
        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss.bom", "jboss-eap-javaee7", "7.0.0-SNAPSHOT");
        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss.bom", "jboss-javaee-7.0-eap-with-tools", "7.0.0-SNAPSHOT");
        ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom(BOM_EAP7_TOOLS);
        ArtifactFilter.AndFilter libsBomFilter = new ArtifactFilter.AndFilter(ArtifactFilter.LIBRARIES, bomFilter);
        PackageLuceneIndexArtifactVisitor packagesIndexerVisitor = new PackageLuceneIndexArtifactVisitor(new File(outDir, LUCENE_SUBDIR_PACKAGES), libsBomFilter);


        for (int i = 0; i < reader.maxDoc(); i++)
        {
            if (liveDocs != null && !liveDocs.get(i))
                continue;
            //if (liveDocs == null || liveDocs.get(i))

            final Document doc = reader.document(i);
            final ArtifactInfo artifact = IndexUtils.constructArtifactInfo(doc, this.context);
            if (artifact == null){
                //LOG.info("IndexUtils.constructArtifactInfo(doc, this.context) returned null: ["+i+"]" + doc.toString());
                // This happens for documents which are not Artifact, e.g. Archetype etc.
                continue;
            }

            artifact.setSha1(StringUtils.lowerCase(artifact.getSha1()));
            artifact.setPackaging(StringUtils.defaultString(artifact.getPackaging()));
            artifact.setClassifier(StringUtils.defaultString(artifact.getClassifier()));

            for (ArtifactVisitor<Object> visitor : Arrays.asList(writerVisitor, basicIndexerVisitor, packagesIndexerVisitor))
            {
                try {
                    visitor.visit(artifact);
                }
                catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed processing " + artifact + " with " + visitor + "\n    " + e.getMessage());
                }
            }
        }


        try {
            writerVisitor.done();
        }
        catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed finishing " + writerVisitor, e);
        }
        try {
            basicIndexerVisitor.done();
        }
        catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed finishing " + basicIndexerVisitor, e);
        }
        try {
            packagesIndexerVisitor.done();
        } catch (Exception e)
        {
            LOG.log(Level.SEVERE, "Failed finishing " + basicIndexerVisitor, e);
        }
        this.context.releaseIndexSearcher(searcher);
    }

    @Override
    public void close() throws IOException
    {
        this.context.close(false);
        this.indexer.closeIndexingContext(this.context, false);
    }



    /**
     * Normal visitor pattern which also allows to call a method after finished and retrieve a resulting object.
     */
    public interface ArtifactVisitor<T>
    {
        void visit(ArtifactInfo artifact);
        public T done();
    }




    /// Test - http://www.programcreek.com/java-api-examples/index.php?source_dir=maven-indexer-master/indexer-core/src/test/java/org/apache/maven/index/FullIndexNexusIndexerTest.java
    void test() throws IOException{

        {
            BooleanQuery bq = new BooleanQuery();
            bq.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.VERSION, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.PACKAGING, "jar" )), BooleanClause.Occur.MUST);

            FlatSearchResponse response = this.indexer.searchFlat( new FlatSearchRequest( bq ) );
        }

        {
            Query q = new TermQuery(new Term("coords", "org.jboss:parent:19"));
            FlatSearchResponse response = this.indexer.searchFlat(new FlatSearchRequest(q));

            //response.getResults().iterator().next()
        }
    }
}
