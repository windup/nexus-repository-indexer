package org.jboss.windup.maven.nexusindexer;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoRecord;
import org.apache.maven.index.Field;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


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
     */
    private void writeMetadataTo(File outDir, DependencyRepository repository, OutputFormat outputFormat) throws IOException
    {
        outDir.mkdirs();

        // Maven repo index
        final IndexSearcher searcher = this.context.acquireIndexSearcher();
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


        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss", "jboss-parent", "19");
        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss.bom", "jboss-eap-javaee7", "7.0.0-SNAPSHOT");
        //ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom("org.jboss.bom", "jboss-javaee-7.0-eap-with-tools", "7.0.0-SNAPSHOT");
        ArtifactFilter bomFilter = new BomBasedArtifactFilterFactory().createArtifactFilterFromBom(BOM_EAP7_TOOLS);
        ArtifactFilter.AndFilter libsBomFilter = new ArtifactFilter.AndFilter(ArtifactFilter.LIBRARIES, bomFilter);

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

        // https://issues.redhat.com/browse/WINDUP-2765 to fix https://issues.sonatype.org/browse/OSSRH-60950
        final BooleanQuery missingArtifactsQuery = new BooleanQuery();
        missingArtifactsQuery.add(new PrefixQuery(new Term(ArtifactInfo.GROUP_ID, "org.springframework.")), BooleanClause.Occur.MUST);
        missingArtifactsQuery.add(new PrefixQuery(new Term(ArtifactInfo.ARTIFACT_ID, "spring-")), BooleanClause.Occur.MUST);
        missingArtifactsQuery.add(new TermQuery(new Term(ArtifactInfo.PACKAGING, "module")), BooleanClause.Occur.MUST);
        final TotalHitCountCollector missingArtifactsQueryCountCollector = new TotalHitCountCollector();
        searcher.search(missingArtifactsQuery, missingArtifactsQueryCountCollector);
        final int artifactsCount = missingArtifactsQueryCountCollector.getTotalHits();
        LOG.log(Level.INFO, String.format("Found %d artifacts to be fixed in repository %s", artifactsCount, repository.getId()));
        if (artifactsCount > 0) {
            final TopDocs docs = searcher.search(missingArtifactsQuery, artifactsCount);
            Arrays.asList(searcher.search(missingArtifactsQuery, docs.totalHits).scoreDocs)
                    .forEach(doc -> {
                                try {
                                    final String uInfo = searcher.doc(doc.doc).get(ArtifactInfo.UINFO);
                                    final String[] gav = uInfo.split("\\" + ArtifactInfoRecord.FS);
                                    // e.g. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE-javadoc.jar.sha1
                                    final String sha1FileUrl = new StringBuilder(repository.getUrl())
                                            // groupId
                                            .append("/").append(gav[0].replace('.', '/'))
                                            // artifactId
                                            .append("/").append(gav[1])
                                            // version
                                            .append("/").append(gav[2])
                                            // file name
                                            .append("/").append(gav[1]).append("-").append(gav[2]).append(".jar.sha1").toString();
                                    final URL url = new URL(sha1FileUrl);
                                    final BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                                    // the hash sha1 file should be always a 1 line text file
                                    final String sha1 = in.readLine();
                                    in.close();
                                    // check the hash has the expected length
                                    if (!(sha1 != null && sha1.length() == 40)) {
                                        LOG.log(Level.WARNING, String.format("Dependency %s the retrieve hash (%s) is not valid so it will be skipped", uInfo, sha1));
                                        return;
                                    }
                                    LOG.log(Level.FINE, String.format("Dependency %s hash is %s", uInfo, sha1));
                                    // check the doc is really not in the index because when the issue on the Maven Index will be fixed
                                    // this check will prevent our indexer to add twice the same Artifact to our index
                                    final BooleanQuery hashQuery = new BooleanQuery();
                                    hashQuery.add(new TermQuery(new Term(ArtifactInfo.SHA1, sha1)), BooleanClause.Occur.MUST);
                                    hashQuery.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, gav[0])), BooleanClause.Occur.MUST);
                                    hashQuery.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, gav[1])), BooleanClause.Occur.MUST);
                                    hashQuery.add(new TermQuery(new Term(ArtifactInfo.VERSION, gav[2])), BooleanClause.Occur.MUST);
                                    // must add also the classifier condition to search only for jars
                                    // because there are artifacts with the same hash
                                    // e.g. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE.jar.sha1 85f79121fdaabcbcac085d0d4aad34af9f8dbba2
                                    // and https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE-javadoc.jar.sha1 85f79121fdaabcbcac085d0d4aad34af9f8dbba2
                                    hashQuery.add(new TermQuery(new Term(ArtifactInfo.CLASSIFIER, Field.NOT_PRESENT)), BooleanClause.Occur.MUST);
                                    final TopDocs jarFound = searcher.search(hashQuery, 1);
                                    if (jarFound.totalHits == 0) {
                                        final ArtifactInfo artifactInfo = new ArtifactInfo(repository.getId(), gav[0], gav[1], gav[2], StringUtils.defaultString(null), "jar");
                                        artifactInfo.setSha1(sha1);
                                        artifactInfo.setPackaging("jar");
                                        for (ArtifactVisitor<Object> visitor : visitors) {
                                            try {
                                                visitor.visit(artifactInfo);
                                            } catch (Exception e) {
                                                LOG.log(Level.SEVERE, String.format("Failed processing %s with %s\n    %s", artifactInfo, visitor, e.getMessage()));
                                            }
                                        }
                                    } else {
                                        LOG.log(Level.INFO, String.format("Dependency %s is NOT missing anymore in the source index", uInfo));
                                    }
                                } catch (IOException e) {
                                    LOG.log(Level.SEVERE, String.format("Document %s management has failed", doc));
                                    e.printStackTrace();
                                }
                            }
                    );
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
