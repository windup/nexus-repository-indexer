package org.jboss.windup.maven.nexusindexer.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Bits;
import org.jboss.windup.maven.nexusindexer.ZipUtil;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class LuceneIndexServiceBase implements Closeable
{
    private static final Logger LOG = Logger.getLogger(LuceneIndexServiceBase.class.getName());

    protected File directory;
    protected Directory index;
    protected IndexReader reader;
    protected IndexSearcher searcher;


    public LuceneIndexServiceBase(File directory)
    {
        if (directory == null || !directory.exists() || !directory.isDirectory())
            throw new IllegalArgumentException("Directory does not exist: " + directory);

        this.directory = directory;
        try
        {
            initialize();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load Lucene index due to: " + e.getMessage(), e);
        }
    }


    private void initialize() throws IOException
    {
        this.index = new SimpleFSDirectory(this.directory);
        this.reader = DirectoryReader.open(index);
        this.searcher = new IndexSearcher(reader);
    }


    @Override
    public final void close()
    {
        try
        {
            this.reader.close();
            this.index.close();
        }
        catch (Exception e)
        {
            LOG.warning("Failed to close Lucene index in: " + this.directory + " due to: " + e.getMessage());
        }
    }


    public int visitAllDocuments(ZipUtil.Visitor<Document> visitor)
    {
        int count = 0;
        final IndexReader reader = searcher.getIndexReader();
        Bits liveDocs = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.maxDoc(); i++)
        {
            if (liveDocs != null && !liveDocs.get(i))
                continue;

            try
            {
                Document doc = reader.document(i);
                visitor.visit(doc);
                count++;
            }
            catch (IOException ex)
            {
                LOG.log(Level.WARNING, "Error reading Lucene document #" + i + ": " + ex.getMessage(), ex);
            }
        }
        return count;
    }


    /**
     * Visits each document having @fieldName field with @value using given visitor.
     * @param maxHits Maximum number of top matching documents to visit.
     */
    public void findByField(String fieldName, String value, int maxHits, ZipUtil.Visitor<Document> visitor)
    {
        Query query = new TermQuery(new Term(fieldName, value));
        try
        {
            TopDocs results = this.getSearcher().search(query, maxHits);
            for (ScoreDoc scoreDoc : results.scoreDocs)
            {
                Document doc = this.getSearcher().doc(scoreDoc.doc);
                visitor.visit(doc);
            }
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Error finding document with: " + fieldName + " == " + value
                    + "\n    Visitor used: " + visitor
                    + "\n    " + ex.getMessage(), ex);
        }
    }

    /**
     * Visits each document having @fieldName field with @value using given visitor.
     */
    public <T> T findSingle(String fieldName, String value, DocTo<T> converter)
    {
        Query query = new TermQuery(new Term(fieldName, value));
        try
        {
            TopDocs results = this.getSearcher().search(query, 1);
            for (ScoreDoc scoreDoc : results.scoreDocs)
            {
                Document doc = this.getSearcher().doc(scoreDoc.doc);
                return converter.convert(doc);
            }
            return null;
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Error finding single document with: " + fieldName + " == " + value
                    + "\n    " + ex.getMessage(), ex);
        }
    }

    public final IndexSearcher getSearcher()
    {
        return searcher;
    }

    public final File getDirectory()
    {
        return directory;
    }

}
