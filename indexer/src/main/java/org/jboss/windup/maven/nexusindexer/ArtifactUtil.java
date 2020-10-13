package org.jboss.windup.maven.nexusindexer;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Field;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;

import java.io.IOException;

public class ArtifactUtil
{
    public static boolean isArtifactAlreadyIndexed(Indexer indexer, IndexingContext context, String sha1, ArtifactInfo artifactInfo) throws IOException
    {
        // check the doc is really not in the index because when the issue on the Maven Index will be fixed
        // this check will prevent our indexer to add twice the same Artifact to our index
        final BooleanQuery hashQuery = new BooleanQuery();
        hashQuery.add(indexer.constructQuery(MAVEN.SHA1, new SourcedSearchExpression(sha1)), BooleanClause.Occur.MUST);
        hashQuery.add(indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression(artifactInfo.getGroupId())), BooleanClause.Occur.MUST);
        hashQuery.add(indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression(artifactInfo.getArtifactId())), BooleanClause.Occur.MUST);
        hashQuery.add(indexer.constructQuery(MAVEN.VERSION, new SourcedSearchExpression(artifactInfo.getVersion())), BooleanClause.Occur.MUST);
        // must add also the classifier condition to search only for jars
        // because there are artifacts with the same hash
        // e.g. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE.jar.sha1 85f79121fdaabcbcac085d0d4aad34af9f8dbba2
        // and https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.3.2.RELEASE/spring-boot-starter-web-2.3.2.RELEASE-javadoc.jar.sha1 85f79121fdaabcbcac085d0d4aad34af9f8dbba2
        hashQuery.add(indexer.constructQuery(MAVEN.CLASSIFIER, new SourcedSearchExpression(Field.NOT_PRESENT)), BooleanClause.Occur.MUST_NOT);
        FlatSearchResponse response = indexer.searchFlat(new FlatSearchRequest(hashQuery, context));
        return response.getTotalHitsCount() > 0;
    }
}
