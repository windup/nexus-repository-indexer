package org.jboss.windup.maven.nexusindexer.client;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.windup.maven.nexusindexer.RepositoryIndexManager;
import org.jboss.windup.maven.nexusindexer.ZipUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class QueryAndListIT
{
    @Test
    public void testPackageIndexQuery()
    {
        File indexDir = new File("target/", RepositoryIndexManager.LUCENE_SUBDIR_PACKAGES);
        LuceneIndexServiceBase indexService = new LuceneIndexServiceBase(indexDir);

        final List<String> artifacts = new ArrayList<>(64);
        // org.hibernate:hibernate-core::jar:4.3.8.Final
        indexService.findByField(DocTo.Fields.PACKAGE, "org.hibernate.dialect",  100, new ZipUtil.Visitor<Document>()
        {
            public void visit(Document doc)
            {
                artifacts.add(DocTo.COORD_GAVCP_TAKEOVER.convert(doc));
            }
        });
        System.out.println("testPackageIndexQuery() - Docs found: " + artifacts.size());
        Assert.assertTrue("Some docs were found", artifacts.size() > 0);
        Assert.assertEquals("org.hibernate:hibernate-core:4.3.8.Final:jar:", artifacts.get(0));
    }

    @Test
    public void testVisit(){
        File indexDir = new File("target/", RepositoryIndexManager.LUCENE_SUBDIR_PACKAGES);
        LuceneIndexServiceBase indexService = new LuceneIndexServiceBase(indexDir){};
        int count = indexService.visitAllDocuments(new ZipUtil.Visitor<Document>() // TODO: Move to Util.
        {
            @Override
            public void visit(Document doc)
            {
                Artifact artifact = DocTo.ARTIFACT.convert(doc);
            }
        });
        System.out.println("testVisit() - Docs found: " + count);
        Assert.assertTrue("Some docs were found", count > 1);
    }

    @Test
    public void testFindSingle(){
        File indexDir = new File("target/", RepositoryIndexManager.LUCENE_SUBDIR_CHECKSUMS);
        LuceneIndexServiceBase indexService = new LuceneIndexServiceBase(indexDir){};
        // 05ccde9cb5e3071eaadf5d87a84b4d0aba43b119 org.apache.commons:commons-lang3:jar::3.3
        String result = indexService.findSingle(DocTo.Fields.SHA1, "05ccde9cb5e3071eaadf5d87a84b4d0aba43b119", new DocTo<String>()
        {
            @Override
            public String convert(Document doc)
            {
                return doc.get(DocTo.Fields.ARTIFACT_ID);
            }
        });
        System.out.println("testFindSingle() - artifactId found: " + result);
        Assert.assertNotNull(result);
        Assert.assertEquals("commons-lang3", result);
    }



    @Test @Ignore
    public void testSomePotentiallyUsefulCode(){
        File indexDir = new File("target/", RepositoryIndexManager.LUCENE_SUBDIR_CHECKSUMS);
        LuceneIndexServiceBase indexService = new LuceneIndexServiceBase(indexDir){};

        // Test - http://www.programcreek.com/java-api-examples/index.php?source_dir=maven-indexer-master/indexer-core/src/test/java/org/apache/maven/index/FullIndexNexusIndexerTest.java
        {
            BooleanQuery bq = new BooleanQuery();
            bq.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.VERSION, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.PACKAGING, "jar" )), BooleanClause.Occur.MUST);

            // org.apache.maven.indexer - could be useful:
            //FlatSearchResponse response = indexService.getSearcher().search(bq, results);
        }
    }

}
