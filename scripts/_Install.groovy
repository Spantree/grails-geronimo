//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

// The path to the configuration file
def configDir  = "${basedir}/grails-app/conf/grails-geronimo"
def configPath = "${configDir}/_GeronimoConfig.groovy"
def configFile = new File(configPath)

confirmInput = { String message ->
    ant.input(message: message, addproperty: "confirm.message", validargs: "y,n")
    ant.antProject.properties."confirm.message" == "y"
}

if ( !configFile.exists() || confirmInput("Overwrite existing grails-geronimo configuration?") ) {

    println "Creating ${configFile.absolutePath}"
    ant.mkdir(dir: "${configDir}")

    configFile.write """\
/////////////////////////////////
// GRAILS-GERONIMO CONFIGURATION

// Default setting for interfacing with the geronimo server
def geronimoDefaultSettings = [
    // The location of the geronimo installation
    'geronimo-home' : '',
    // The user name necessary for deploying to the server
    'geronimo-user' : 'system',
    // The password necessary for deploying to the server
    'geronimo-pass' : 'manager',
    // The temporary staging dir for deploying library jars
    'geronimo-staging-dir' : grailsSettings.projectWarExplodedDir.toString() + "_geronimo",
    // If true, cars/rars will not be generated during war build
    'no-geronimo-common-resource-packages' : false,
    // If true, skinny war will not be built during war deployment
    'no-geronimo-war' : false,
    // If true, cars will not be deployed during war deployment
    'no-geronimo-deploy-common-resource-packages' : false,
    // If true, library jars will not be deployed during war deployment
    'no-geronimo-deploy-libs' : false,
    // The version of geronimo we support
    'geronimo-version' : '2.1.7'
]

// Map from ivy artifacts to maven artifacts
def ivyToMavenArtifactMap = [
	'apache-taglibs:standard' : [ groupId : 'taglibs', artifactId : 'standard' ],
	'org.springframework:org.springframework.transaction' : [ groupId : 'org.springframework', artifactId : 'spring-tx' ],
	'org.springframework:org.springframework.web.servlet' : [ groupId : 'org.springframework', artifactId : 'spring-webmvc' ]
]

// Maven settings for generating car/rar files
def mavenDefaultSettings = [
    'maven-group-id' : 'org.apache.geronimo.plugins',
    'maven-base-dir' : 'target/geronimo',
    'maven-packaging' : 'rar',
	'maven-opts' : '' // Additional command line options to pass to maven build
]

getGeronimoDefaultSettings = {
    return geronimoDefaultSettings
}

getIvyToMavenArtifactMap = {
    return ivyToMavenArtifactMap
}

getMavenDefaultSettings = {
    return mavenDefaultSettings
}

"""

}

println "##########################################\nFor configuration options, see ${configFile.absolutePath}\n##########################################"

