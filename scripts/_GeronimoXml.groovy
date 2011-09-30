import groovy.xml.MarkupBuilder
import org.codehaus.groovy.grails.commons.ConfigurationHolder as grailsConfigHolder

includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoModules.groovy")

// Geronimo XML utilities

// Generates a geronimo web xml for grails war deployment
// Takes an arguments map = [ 
//      xml:(markupBuilder), - the markup builder used for generating the xml
//      groupId:(string), - the group id for this war
//      artifactId:(string), - the artifact id for this war
//      version:(string), - the version for this war (optional - defaults to "0.1")
//      packaging:(string), - the archive type (optional - defaults to "war")
//      contextRoot:(string), - the applications relative server path
//      dependencies:(list) - the list of dependencies to include within the pom for building with maven
buildGeronimoWebXml = { args ->
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
                            //type( dep.packaging )
                        }
                    }
                }
            }

            'non-overridable-classes' {
                filter('javax.transaction')
            }
			//'inverse-classloading'()
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
