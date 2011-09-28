import groovy.xml.MarkupBuilder
import org.codehaus.groovy.grails.commons.ConfigurationHolder as grailsConfigHolder

includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoModules.groovy")

// Utilities for generating XML files

// Generates a geronimo web xml for grails war deployment
// Takes an arguments map = [ 
//      xml:(markupBuilder), - the markup builder used for generating the xml
//      groupId:(string), - the group id for this war
//      artifactId:(string), - the artifact id for this war
//      version:(string), - the version for this war (optional - defaults to "0.1")
//      packaging:(string), - the archive type (optional - defaults to "war")
//      contextRoot:(string), - the applications relative server path
//      dependencies:(list) - the list of dependencies to include within the pom for building with maven
generateGeronimoWebXml = { args ->
    args.xml.mkp.xmlDeclaration(version:'1.0', encoding:"UTF-8")
    args.xml.'web-app'(xmlns:"http://geronimo.apache.org/xml/ns/j2ee/web-1.1") {
        environment(xmlns:"http://geronimo.apache.org/xml/ns/deployment-1.1") {
            moduleId {
                groupId(args.groupId)
                artifactId(args.artifactId)
                version(args.version?:"0.1")
                type(args.packaging?:"war")
            }

            if ( args.dependencies ) {
                dependencies {
                    args.dependencies.each { dep ->
                        dependency() {
                            groupId( dep.getMavenGroupAndArtifactIds( args.ivyToMavenArtifactMap ).groupId )
                            artifactId( dep.getMavenGroupAndArtifactIds( args.ivyToMavenArtifactMap ).artifactId )
                            version( dep.version )
                            type( dep.packaging )
                        }
                    }
                }
            }

            'non-overridable-classes' {
                filter('javax.transaction')
            }
        }
        'context-root'(args.contextRoot)
    }
}

// Returns an arguments map with the default parameters for generating geronimo-web.xml
getDefaultGeronimoWebXmlParams = { dependencies ->
    def xmlParams = [ xml : (new MarkupBuilder(new FileWriter("${stagingDir}/WEB-INF/geronimo-web.xml"))),
      groupId : grailsConfigHolder.config.grails.project.groupId,
      artifactId : grailsAppName,
      version : metadata.getApplicationVersion(),
      packaging : "war",
      contextRoot : grailsAppName ]

    if ( dependencies ) {
        xmlParams.dependencies = dependencies
        xmlParams.ivyToMavenArtifactMap = getIvyToMavenArtifactMap()
    }

    return xmlParams
}

// Populates a pom xml file for building a car
// Takes an arguments map = [ 
//      xml:(markupBuilder), - the markup builder used for generating the xml
//      ivyToMavenArtifactMap:(map) - maps ivy group and artifact ids to maven ids
//      geronimoVersion:(string) - the version of geronimo to use
//      geronimoModule: - a module containing metadata such as artifact id, group id, and dependencies
void generateCarPomXml( def args ) {
    args.xml.project('xsi:schemaLocation': 'http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd') {
        modelVersion('4.0.0')
        parent() {
            groupId('org.apache.geronimo.genesis.config')
            artifactId('project-config')
            version('1.5')
        }
        groupId(args.geronimoModule.groupId)
        artifactId(args.geronimoModule.artifactId)
        name(args.geronimoModule.mavenName)
        packaging(args.geronimoModule.packaging)
        version(args.geronimoModule.version)
        properties() {
            geronimoVersion(args.geronimoVersion)
            projectName(args.name)
        }
        dependencies {
            dependency {
                groupId('org.apache.geronimo.framework')
                artifactId('geronimo-gbean-deployer')
                version('${geronimoVersion}')
                type('car')
                scope('provided')
            }
            dependency {
                groupId('org.apache.geronimo.configs')
                artifactId('j2ee-deployer')
                version('${geronimoVersion}')
                type('car')
                scope('provided')
            }
            dependency() {
                groupId('org.apache.geronimo.framework')
                artifactId('jee-specs')
                version('${geronimoVersion}')
                type('car')
                scope('provided')
            }
            args.geronimoModule.dependencies.each { dep ->
                dependency() {
                    groupId( dep.getMavenGroupAndArtifactIds( args.ivyToMavenArtifactMap ).groupId )
                    artifactId( dep.getMavenGroupAndArtifactIds( args.ivyToMavenArtifactMap ).artifactId )
                    version( dep.version )
                    type( dep.packaging )
                }
            }
        }
        build() {
            plugins() {
                plugin() {
                    groupId('org.apache.geronimo.buildsupport')
                    artifactId('car-maven-plugin')
                    version('${geronimoVersion}')
                    extensions('true')
                    configuration() {
                        archive() {
                            addMavenDescriptor('false')
                        }
                        category('Geronimo Plugins')
                        osiApproved('true')
                        useMavenDependencies() {
                            value('true')
                            includeVersion('true')
                        }
                        commonInstance {
                            "plugin-artifact" {
                                "source-repository"("~/.m2/repository/")
                                "source-repository"("http://repo1.maven.org/maven2/")
                            }
                        }
                    }
                }
            }
        }
    }
}

void generateCarPlanXml( def xml ) {
    xml.module(xmlns:'http://geronimo.apache.org/xml/ns/deployment-1.2') {
        environment {
            'hidden-classes' {
                filter('org.jaxen')
                filter('org.springframework')
                filter('org.apache.cxf')
                filter('org.apache.commons')
            }
        }
    }
}

generateCarPomAndPlanXml = { geronimoModule ->
    def artifactRootPath = "${getMavenSettings().baseDir}/${geronimoModule.artifactId}"
    new File( "$artifactRootPath/" ).mkdirs()
    def pomWriter = new FileWriter("$artifactRootPath/pom.xml")
    generateCarPomXml( 
        [ xml : (new MarkupBuilder(pomWriter)), 
          ivyToMavenArtifactMap : getIvyToMavenArtifactMap(),
          geronimoVersion : getMavenSettings().geronimoVersion, 
          geronimoModule : geronimoModule ]
    )
    
    new File("$artifactRootPath/src/main/plan/").mkdirs()
    def planWriter = new FileWriter("$artifactRootPath/src/main/plan/plan.xml")
    generateCarPlanXml(new MarkupBuilder(planWriter))
}

