package org.jboss.windup.maven.nexusindexer;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Field;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.creator.JarFileContentsIndexCreator;
import org.apache.maven.index.creator.MavenArchetypeArtifactInfoIndexCreator;
import org.apache.maven.index.creator.MavenPluginArtifactInfoIndexCreator;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.creator.OsgiArtifactIndexCreator;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.packer.IndexPacker;
import org.apache.maven.index.packer.IndexPackingRequest;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jboss.forge.addon.dependencies.DependencyRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;


/**
 * Downloads Maven index from given repository and produces a list of all artifacts, using this format: "SHA G:A:V[:C]".
 *
 * @author Ondrej Zizka, ozizka at redhat.com
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class RepositoryIndexManager implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(RepositoryIndexManager.class.getName());

    public enum OutputFormat {
        TEXT,
        LUCENE
    }

    public static final String LUCENE_SUBDIR_CHECKSUMS = "lucene";

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
    public static void generateMetadata(DependencyRepository repository, File indexDir, File outputDir, OutputFormat format) throws Exception
    {
        try (RepositoryIndexManager manager = new RepositoryIndexManager(indexDir, repository))
        {
            LOG.info("Downloading or updating index into " + indexDir.getPath());
            manager.downloadIndexAndUpdate();
            LOG.info("Writing selected Nexus index data to " + outputDir.getPath());
            manager.writeMetadataTo(outputDir, repository, format);
        }
    }

    /**
     * Download the index for the given {@link DependencyRepository}, retrieve the broken artifacts and update them with
     * the right values
     */
    public static void updateNexusIndex(DependencyRepository repository, File indexDir, File outputDir) throws Exception {
        try (RepositoryIndexManager manager = new RepositoryIndexManager(indexDir, repository)) {
            LOG.info("Downloading or updating index into " + indexDir.getPath());
            manager.downloadIndexAndUpdate();
            LOG.info("Update with fixes selected Nexus index data to " + outputDir.getPath());
            manager.updateNexusIndex(outputDir, repository);
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
        indexers.add(plexusContainer.lookup(IndexCreator.class, OsgiArtifactIndexCreator.ID));
        indexers.add(plexusContainer.lookup(IndexCreator.class, MavenPluginArtifactInfoIndexCreator.ID));
        indexers.add(plexusContainer.lookup(IndexCreator.class, MavenArchetypeArtifactInfoIndexCreator.ID));
        indexers.add(plexusContainer.lookup(IndexCreator.class, JarFileContentsIndexCreator.ID));
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
     */
    private void writeMetadataTo(File outDir, DependencyRepository repository, OutputFormat outputFormat) throws IOException
    {
        outDir.mkdirs();

        // Maven repo index
        final IndexSearcher searcher = context.acquireIndexSearcher();
        final IndexReader reader = searcher.getIndexReader();
        Bits liveDocs = MultiFields.getLiveDocs(reader);


        final File textMetadataFile = getMetadataFile(repository, outDir);
        final List<RepositoryIndexManager.ArtifactVisitor<Object>> visitors = new ArrayList<>();

        if (outputFormat.equals(OutputFormat.TEXT))
        {
            SortingLineWriterArtifactVisitor writerVisitor = new SortingLineWriterArtifactVisitor(textMetadataFile, ArtifactFilter.LIBRARIES);
            visitors.add(writerVisitor);
        } else if (outputFormat.equals(OutputFormat.LUCENE))
        {
            LuceneIndexArtifactVisitor basicIndexerVisitor = new LuceneIndexArtifactVisitor(new File(outDir, LUCENE_SUBDIR_CHECKSUMS), ArtifactFilter.LIBRARIES);
            visitors.add(basicIndexerVisitor);
        }

        for (int i = 0; i < reader.maxDoc(); i++)
        {
            if (liveDocs != null && !liveDocs.get(i))
                continue;

            final Document doc = reader.document(i);
            final ArtifactInfo artifact = IndexUtils.constructArtifactInfo(doc, this.context);
            if (artifact == null){
                // This happens for documents which are not Artifact, e.g. Archetype etc.
                continue;
            }

            artifact.setSha1(StringUtils.lowerCase(artifact.getSha1()));
            artifact.setPackaging(StringUtils.defaultString(artifact.getPackaging()));
            artifact.setClassifier(StringUtils.defaultString(artifact.getClassifier()));

            for (ArtifactVisitor<Object> visitor : visitors)
            {
                try {
                    visitor.visit(artifact);
                }
                catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed processing " + artifact + " with " + visitor + "\n    " + e.getMessage());
                }
            }
        }

        final BooleanQuery missingArtifactsQuery = createMissingArtifactsQuery();

        final TotalHitCountCollector missingArtifactsQueryCountCollector = new TotalHitCountCollector();
        searcher.search(missingArtifactsQuery, missingArtifactsQueryCountCollector);
        final int artifactsCount = missingArtifactsQueryCountCollector.getTotalHits();
        LOG.log(Level.INFO, String.format("Found %d artifacts to be added in repository %s", artifactsCount, repository.getId()));
        final AtomicInteger managed = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger(0);
        if (artifactsCount > 0) {
            Arrays.asList(searcher.search(missingArtifactsQuery, artifactsCount).scoreDocs)
                    .parallelStream()
                    .forEach(doc -> {
                                try {
                                    final ArtifactInfo wrongArtifactInfo = IndexUtils.constructArtifactInfo(searcher.doc(doc.doc), this.context);
                                    final String sha1 = ArtifactDownloader.getJarSha1(repository.getUrl(), wrongArtifactInfo);
                                    if (!ArtifactUtil.isArtifactAlreadyIndexed(indexer, this.context, sha1, wrongArtifactInfo)) {
                                        final ArtifactInfo artifactInfo = new ArtifactInfo(repository.getId(),
                                                wrongArtifactInfo.getGroupId(), wrongArtifactInfo.getArtifactId(),
                                                wrongArtifactInfo.getVersion(), StringUtils.defaultString(null), "jar");
                                        artifactInfo.setSha1(sha1);
                                        artifactInfo.setPackaging("jar");
                                        for (ArtifactVisitor<Object> visitor : visitors) {
                                            try {
                                                visitor.visit(artifactInfo);
                                            } catch (Exception e) {
                                                LOG.log(Level.SEVERE, String.format("Failed processing %s with %s%n    %s", artifactInfo, visitor, e.getMessage()));
                                            }
                                        }
                                        if (managed.incrementAndGet() % 5000 == 0)
                                        {
                                            LOG.log(Level.INFO, String.format("Managed %d/%d artifacts ", managed.get(), artifactsCount));
                                        }
                                    } else {
                                        LOG.log(Level.INFO, String.format("Dependency %s is NOT missing anymore in the source index", wrongArtifactInfo.getUinfo()));
                                    }
                                } catch (IOException e) {
                                    errors.incrementAndGet();
                                    LOG.log(Level.WARNING, String.format("Document %s management has failed%n    %s", doc, e.getMessage()));
                                }
                            }
                    );
            LOG.log(Level.INFO, String.format("Managed %d/%d artifacts with %d artifacts not managed for problems (check log above)", managed.get(), artifactsCount, errors.get()));
        }

        for (ArtifactVisitor<Object> visitor : visitors)
        {
            try {
                visitor.done();
            } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed finishing " + visitor, e);
            }
        }
        this.context.releaseIndexSearcher(searcher);
    }

    // This query is created to address certain missing artifacts from the index.
    // See https://issues.redhat.com/browse/WINDUP-2765 and https://issues.sonatype.org/browse/OSSRH-60950
    private BooleanQuery createMissingArtifactsQuery() {
        final BooleanQuery missingArtifactsQuery = new BooleanQuery();

        // Query for searching all the docs in the index with the 'packaging' (aka the extension) that is 'module'
        // e.g. Lucene query "+g:org.springframework.boot +a:spring-boot-starter-tomcat  +v:2.3.* +p:module"
        // https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/
        final TermQuery artifactsWithModuleQuery = new TermQuery(new Term(ArtifactInfo.PACKAGING, "module"));

        // Query for searching all the docs in the index with the 'packaging' (aka the extension) that is 'pom.sha512'
        // e.g. Lucene query "+g:org.springdoc +a:springdoc-openapi-common +v:1.4.? +p:pom.sha512"
        // https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-common/1.4.3/
        // https://repo1.maven.org/maven2/org/apache/ant/ant-commons-logging/1.8.0/
        final TermQuery artifactsWithPomSha512Query = new TermQuery(new Term(ArtifactInfo.PACKAGING, "pom.sha512"));

        missingArtifactsQuery.add(artifactsWithModuleQuery, BooleanClause.Occur.SHOULD);
        missingArtifactsQuery.add(artifactsWithPomSha512Query, BooleanClause.Occur.SHOULD);

        // Searches for artifacts with "bundle" packaging and no Symbolic Bundle Name to cater for
        // artifacts like https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.12.3
        // See also https://issues.redhat.com/browse/WINDUP-3300
        final BooleanQuery missingBundleArtifactsClause = new BooleanQuery();
        final TermQuery artifactsWithBundleQuery = new TermQuery(new Term(ArtifactInfo.PACKAGING, "bundle"));
        final TermRangeQuery artifactsWithNoSymNameQuery = TermRangeQuery.newStringRange(ArtifactInfo.BUNDLE_SYMBOLIC_NAME, "*", "*", true, true);
        missingBundleArtifactsClause.add(artifactsWithBundleQuery, BooleanClause.Occur.MUST);
        missingBundleArtifactsClause.add(artifactsWithNoSymNameQuery, BooleanClause.Occur.MUST_NOT);

        missingArtifactsQuery.add(new BooleanClause(missingBundleArtifactsClause, BooleanClause.Occur.SHOULD));

        return missingArtifactsQuery;
    }

    private void updateNexusIndex(File outputDir, DependencyRepository repository) throws IOException, ComponentLookupException
    {
        outputDir.mkdirs();
        final BooleanQuery missingArtifactsQuery = new BooleanQuery();
        // we want "module" artifacts
        missingArtifactsQuery.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "module" ) ), BooleanClause.Occur.SHOULD );
        // we want "pom.sha512" artifacts
        missingArtifactsQuery.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "pom.sha512" ) ), BooleanClause.Occur.SHOULD );
        // we want main artifacts only (no classifier)
        missingArtifactsQuery.add( indexer.constructQuery( MAVEN.CLASSIFIER, new SourcedSearchExpression( Field.NOT_PRESENT ) ), BooleanClause.Occur.MUST_NOT );
        final IteratorSearchRequest request = new IteratorSearchRequest( missingArtifactsQuery, Collections.singletonList(context));
        final IteratorSearchResponse response = indexer.searchIterator(request);
        final int artifactsCount = response.getTotalHitsCount();
        LOG.log(Level.INFO, String.format("Found %d artifacts to be fixed in repository '%s'", artifactsCount, repository.getId()));
        final AtomicInteger managed = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger(0);
        final List<ArtifactContext> artifactsToBeDeleted = new ArrayList<>();
        final List<ArtifactContext> artifactsToBeAdded = new ArrayList<>();
        StreamSupport.stream(response.spliterator(), true)
                .forEach(artifactInfo -> {
                    try {
                        final String sha1 = ArtifactDownloader.getJarSha1(repository.getUrl(), artifactInfo);
                        if (!ArtifactUtil.isArtifactAlreadyIndexed(indexer, this.context, sha1, artifactInfo)) {
                            LOG.log(Level.FINE, String.format("Deleting artifact: {}", artifactInfo));
                            artifactsToBeDeleted.add(new ArtifactContext(null, null, null, artifactInfo, null));
                            artifactInfo.setSha1(sha1);
                            artifactInfo.setPackaging("jar");
                            artifactInfo.setFileExtension("jar");
                            artifactsToBeAdded.add(new ArtifactContext(null, null, null, artifactInfo, null));
                            if (managed.incrementAndGet() % 5000 == 0)
                            {
                                LOG.log(Level.INFO, String.format("Managed %d/%d artifacts ", managed.get(), artifactsCount));
                            }
                        } else {
                            LOG.log(Level.INFO, String.format("Dependency %s is NOT wrong anymore in the source index", artifactInfo.getUinfo()));
                        }
                    }
                    catch (IOException e) {
                        errors.incrementAndGet();
                        LOG.log(Level.WARNING, String.format("Document %s management has failed%n    %s", artifactInfo, e.getMessage()));
                    }
                });
        LOG.log(Level.INFO, String.format("Managed %d/%d artifacts with %d artifacts not managed for problems (check log above).%nTime to update the index", managed.get(), artifactsCount, errors.get()));
        indexer.deleteArtifactsFromIndex(artifactsToBeDeleted, context);
        indexer.addArtifactsToIndex(artifactsToBeAdded, context);
        LOG.log(Level.INFO, String.format("Index updated so moving forward to pack it in %s", outputDir));
        final IndexPacker packer = plexusContainer.lookup(IndexPacker.class);
        final IndexSearcher indexSearcher = context.acquireIndexSearcher();
        try {
            final IndexPackingRequest indexPackingRequest = new IndexPackingRequest(context, indexSearcher.getIndexReader(), outputDir);
            indexPackingRequest.setCreateChecksumFiles(true);
            indexPackingRequest.setCreateIncrementalChunks(true);
            packer.packIndex(indexPackingRequest);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, String.format("Cannot zip index;%n", e.getMessage()));
        } finally {
            context.releaseIndexSearcher(indexSearcher);
        }
        LOG.log(Level.INFO, String.format("Index packed", managed.get(), artifactsCount, errors.get()));
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

}
