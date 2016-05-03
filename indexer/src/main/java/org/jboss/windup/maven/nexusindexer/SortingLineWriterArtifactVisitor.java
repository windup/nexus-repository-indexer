package org.jboss.windup.maven.nexusindexer;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.collections4.list.TreeList;
import org.apache.maven.index.ArtifactInfo;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class SortingLineWriterArtifactVisitor implements RepositoryIndexManager.ArtifactVisitor<Object>
{
    private static final Logger LOG = Logger.getLogger(SortingLineWriterArtifactVisitor.class.getName());

    private final OutputStreamWriter writer;
    private final File outFile;
    // Use a TreeList because it is faster to insert and sort.
    private final List<String> lines = new TreeList<>();
    private final ArtifactFilter filter;


    public SortingLineWriterArtifactVisitor(File outFile, ArtifactFilter filter)
    {
        this.outFile = outFile;
        try
        {
            this.writer = new FileWriter(outFile);
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed writing to  " + outFile.getPath());
        }
        this.filter = filter;
    }


    @Override
    public void visit(ArtifactInfo artifact)
    {
        if (!this.filter.accept(artifact))
            return;
        // G:A:[P:[C:]]V
        // Unfortunately, G:A:::V leads to empty strings instead of nulls, see FORGE-2230.
        StringBuilder line = new StringBuilder();
        // Add to the text file
        line.append(artifact.getSha1()).append(' ');
        line.append(artifact.getGroupId()).append(":");
        line.append(artifact.getArtifactId()).append(":");
        // if (info.getPackaging() != null)
        line.append(artifact.getPackaging()).append(":");
        // if (info.getClassifier() != null)
        line.append(artifact.getClassifier()).append(":");
        line.append(artifact.getVersion());
        line.append("\n");
        lines.add(line.toString());
    }


    public Object done()
    {
        Collections.sort(lines);
        try
        {
            for (String line : lines)
                writer.append(line);
            this.writer.close();
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed writing sorted lines to writer: " + ex.getMessage(), ex);
        }
        this.lines.clear();
        return null;
    }



}
