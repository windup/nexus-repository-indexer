package org.jboss.windup.maven.nexusindexer.client;


import java.io.File;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.maven.index.ArtifactInfo;
import org.jboss.windup.maven.nexusindexer.RepositoryIndexManager;
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
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term(ArtifactInfo.GROUP_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.ARTIFACT_ID, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.VERSION, "maven-archetype" )), BooleanClause.Occur.MUST);
            bq.add(new TermQuery(new Term(ArtifactInfo.PACKAGING, "jar" )), BooleanClause.Occur.MUST);

            // org.apache.maven.indexer - could be useful:
            //FlatSearchResponse response = indexService.getSearcher().search(bq, results);
        }
    }

}
