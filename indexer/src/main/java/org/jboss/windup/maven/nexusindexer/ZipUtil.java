package org.jboss.windup.maven.nexusindexer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:jesse.sightler@gmail.com">Jesse Sightler</a>
 */
public class ZipUtil
{
    public interface Visitor<T>
    {
        void visit(T item);
    }

    /**
     * Scans the JAR file and calls the visitor for each class or package encountered. Packages may occur multiple times if the zip file index is not
     * sorted.
     * 
     * @param zipFilePath Path to the zip file
     * @param packagesOnly Return package names rather than class names.
     * @param onClassFound Callback function for each class found
     */
    public static void scanClassesInJar(Path zipFilePath, boolean packagesOnly, Visitor<String> onClassFound) throws IOException
    {
        try (final InputStream is = new FileInputStream(zipFilePath.toFile()))
        {
            try
            {
                ZipInputStream zis = new ZipInputStream(is);
                ZipEntry entry;
                String lastPackageSubpath = null;
                while ((entry = zis.getNextEntry()) != null)
                {
                    String subPath = entry.getName();
                    if (!subPath.endsWith(".class"))
                        continue;

                    if (packagesOnly)
                    {
                        String packageSubpath = StringUtils.substringBeforeLast(subPath, "/");
                        if (packageSubpath.equals(lastPackageSubpath))
                            continue;
                        lastPackageSubpath = packageSubpath;
                        onClassFound.visit(packageSubpath.replace('/', '.'));
                    }
                    else
                    {
                        String qualifiedName = classFilePathToClassname(subPath);
                        onClassFound.visit(qualifiedName);
                    }
                }
            }
            catch (IOException ex)
            {
                throw new IOException("Could not read ZIP file: " + zipFilePath + " Due to: " + ex.getMessage());
            }
        }
    }

    /**
     * Converts a path to a class file (like "foo/bar/My.class" or "foo\\bar\\My.class") to a fully qualified class name
     * (like "foo.bar.My").
     */
    public static String classFilePathToClassname(String relativePath)
    {
        if (relativePath == null)
            return null;

        final int pos = relativePath.lastIndexOf(".class");
        if (pos < 0 && relativePath.lastIndexOf(".java") < 0)
            throw new IllegalArgumentException("Not a .class/.java file path: " + relativePath);

        relativePath = FilenameUtils.separatorsToUnix(relativePath);

        if (relativePath.startsWith("/"))
        {
            relativePath = relativePath.substring(1);
        }

        if (relativePath.startsWith("src/main/java/"))
        {
            relativePath = relativePath.substring("src/main/java/".length());
        }

        if (relativePath.startsWith("WEB-INF/classes/"))
        {
            relativePath = relativePath.substring("WEB-INF/classes/".length());
        }

        if (relativePath.startsWith("WEB-INF/classes.jdk15/"))
        {
            relativePath = relativePath.substring("WEB-INF/classes.jdk15/".length());
        }

        if (relativePath.endsWith(".class"))
        {
            relativePath = relativePath.substring(0, relativePath.length() - ".class".length());
        }
        else if (relativePath.endsWith(".java"))
        {
            relativePath = relativePath.substring(0, relativePath.length() - ".java".length());
        }

        String qualifiedName = relativePath.replace("/", ".");
        return qualifiedName;
    }
}
