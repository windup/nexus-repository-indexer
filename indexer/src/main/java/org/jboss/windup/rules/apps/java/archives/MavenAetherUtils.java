package org.jboss.windup.rules.apps.java.archives;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
 *
 *  @author <a href="http://ondra.zizka.cz/">Ondrej Zizka, zizka@seznam.cz</a>
 */
public class MavenAetherUtils
{
    private static final Logger log = Logger.getLogger( MavenAetherUtils.class.getName() );


    /**
     * @return a directory which is able to serve as a Maven repository.
     *      This may be the user's local Maven repository.
     */
    public static Path getWorkingRepoPath()
    {
        // Try to find the default repo.
        File defaultRepo = new File(FileUtils.getUserDirectoryPath(), ".m2/repository");
        if (defaultRepo.exists() && defaultRepo.canWrite())
            return defaultRepo.toPath();

        // Otherwise let's use a temp dir.
        try
        {
            return Files.createTempDirectory("WindupNexusIndexerMavenRepo");
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Error creating temporary directory.");
        }
    }


    /**
     * @return A list of repositories to use for resolving artifacts.
     *      Currently Maven Central and JBoss Repository.
     */
    public static List<RemoteRepository> getRepositories(RepositorySystem system, RepositorySystemSession session)
    {
        return new LinkedList()
        {
            {
                add(new RemoteRepository.Builder("jboss", "default", "http://repository.jboss.org/nexus/content/groups/public/").build());
                add(new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build());
            }
        };
    }

    /**
     * @return A new Maven repository system capable of default HTTP transports.
     */
    public static RepositorySystem newRepositorySystem()
    {
        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter factories.
         */
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler()
        {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable ex)
            {
                throw new RuntimeException("Failed creating a RepositorySystem: " + ex.getMessage(), ex);
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    /**
     * @return A Resolver session for the given system working with given local repository.
     */
    public static RepositorySystemSession createSession(RepositorySystem system, Path repoPath)
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(repoPath.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }





}
