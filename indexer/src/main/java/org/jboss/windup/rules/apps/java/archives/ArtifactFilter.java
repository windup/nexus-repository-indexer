package org.jboss.windup.rules.apps.java.archives;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.index.ArtifactInfo;


/**
 * A filter for Maven artifacts.
 *
 * @author Ondrej Zizka, zizka@seznam.cz
 */
public interface ArtifactFilter
{
    boolean accept(ArtifactInfo artifact);


    public static final class AndFilter implements ArtifactFilter
    {
        List<ArtifactFilter> filters = new ArrayList();
        boolean or = false;

        public AndFilter(ArtifactFilter... filters_)
        {
            this.filters = Arrays.asList(filters_);
        }

        public AndFilter(boolean or, ArtifactFilter... filters_)
        {
            this.filters = Arrays.asList(filters_);
            this.or = or;
        }

        @Override public boolean accept(ArtifactInfo artifact)
        {
            for (ArtifactFilter filter : filters)
            {
                if (filter.accept(artifact) ^ !or)
                    return or;
            }
            return !or;
        }
    }


    /**
     * Skips artifacts that are not libraries - tests, pom, sources, javadoc, Maven archetypes and plugins, etc.
     */
    public static ArtifactFilter LIBRARIES = new ArtifactFilter()
    {
        /*
          groupId stats: cat central.archive-metadata.txt | cut -d' ' -f2 | cut -d: -f1 | sort | uniq -c
            30817	com.google.apis
            16265	org.apache.camel
            10905	io.fabric8.jube.images.fabric8
            9056	org.jboss.forge.addon
            8677	org.wicketstuff
            8620	org.eclipse.jetty
            8418	io.fabric8.devops.apps
            8402	org.apache.servicemix.bundles
            8385	com.twitter
            8231	io.fabric8
            7925	org.apache.cxf
            7592	com.sun.jersey.samples
            7331	org.xwiki.commons
            7037	org.kuali.rice
            6992	org.ow2.jonas
            6898	com.amazonaws
            6792	org.glassfish.hk2
            6414	org.glassfish.jersey.examples
            6368	org.drools
            6069	org.infinispan
        */

        /*
           classifier stats: cat central.archive-metadata.txt | cut -d' ' -f2 | cut -d: -f4 | sort | uniq -c | sort -b -n -r
            1106754
              12603 kubernetes
              10385 src
               9748 project
               8723 site
               8656 bin
               7717 source-release
               5962 jar-with-dependencies
               4406 config
               4268 features
               3540 forge-addon
               3179 classes
               3055 app
               2535 bundle
               2486 p2metadata
               2486 p2artifacts
               2173 project-src
               2157 groovydoc
               2063 image
               1955 dist
               1672 distribution
               1565 ipojo
               1331 gf-project
               1223 jdk14
               1181 indy
               1113 assembly
               1112 all
               1050 shaded
                991 resources
                929 mod
                862 gf-project-src
                840 scripts
         */
        private static final String SKIPPED =
            "javadoc javadocs docs groovydoc site"
            + " source sources src source-release project-src gf-project-src"
            + " test tests test-sources tests-sources test-javadoc tests-javadoc"
            + " maven-archetype maven-plugin"
            + " bin app bundle image dist distribution assembly resources scripts";
        private final Set<String> SKIPPED_CLASSIFIERS = new HashSet<>(Arrays.asList(SKIPPED.split(" ")));


        /*
          packaging stats: cat central.archive-metadata.txt | cut -d: -f3 | sort | uniq -c
            878591	jar
            106671	bundle
            54782	zip
            50263	war
            27841	maven-plugin
            19994	xml
            19506	hk2-jar
            15644	tar.gz
            14611	aar
            13584	maven-archetype
            9248	json
            5705	distribution-base-zip
            3635	distribution-fragment
            3529	yml
            3350	nbm
            3035	hpi
            2768	wsdl
            2570	tar.bz2
            2486	car
            2036	sonar-plugin
            1973	nexus-plugin
            1797	jdocbook
            1728	eclipse-plugin
            1461	rar
            1415	apk
            1377	txt
            1122	ear
            1077	xsd
            977	presto-plugin
            939	apklib
            939	ejb
            852	mule
            794	sca-contribution-jar
            746	html
            725	swc
        */
        private final Set<String> SKIPPED_PACKAGINGS  = new HashSet<>(Arrays.asList(
            "png eclipse-repository xhtml ${packaging.type} ${lifecycle} ${packaging}"
            + " jbi-service-unit eclipse-test-plugin atlassian-plugin sh cfg list tree jszip"
            + " pdf eclipse-feature eclipse-plugin swf jangaroo swc html xsd txt apk jdocbook nexus-plugin"
            + " sonar-plugin nbm yml maven-archetype maven-plugin".split(" ")));

        private ArtifactInfo cachedLast = null;
        private boolean cachedLastResult = false;

        @Override
        public boolean accept(ArtifactInfo artifact)
        {
            if(artifact == cachedLast) // Yes, a reference comparison.
                return cachedLastResult;

            cachedLast = artifact;
            cachedLastResult = false;

            if (artifact == null)
                return false;
            if (artifact.getSha1() == null)
                return false;
            if (artifact.getSha1().length() != 40)
                return false;
            if ("tests".equals(artifact.getArtifactId()))
                return false;
            if ("pom".equals(artifact.getPackaging()))
                return false;
            if (SKIPPED_CLASSIFIERS.contains(artifact.getClassifier()))
                return false;
            if (SKIPPED_PACKAGINGS.contains(artifact.getPackaging()))
                return false;

            cachedLastResult = true;
            return true;
        }
    };

}
