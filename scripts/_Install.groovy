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

// Setting for interfacing with the geronimo server
def geronimoSettings = [
    home : '',
    user : 'system',
    pass : 'manager',
    version : '2.1.7'
]

// Map from ivy artifacts to maven artifacts
def ivyToMavenArtifactMap = [
	'apache-taglibs:standard' : [ groupId : 'taglibs', artifactId : 'standard' ],
	'org.springframework:org.springframework.transaction' : [ groupId : 'org.springframework', artifactId : 'spring-tx' ],
	'org.springframework:org.springframework.web.servlet' : [ groupId : 'org.springframework', artifactId : 'spring-webmvc' ]
]

// Maven settings for generating car files
def mavenSettings = [
    geronimoVersion : geronimoSettings.version,
    groupId : 'org.apache.geronimo.plugins',
    baseDir : 'target/geronimo',
    packaging : 'car'
]

getGeronimoSettings = {
    return geronimoSettings
}

getIvyToMavenArtifactMap = {
    return ivyToMavenArtifactMap
}

getMavenSettings = {
    return mavenSettings
}

"""

}

println "##########################################\nFor configuration options, see ${configFile.absolutePath}\n##########################################"

