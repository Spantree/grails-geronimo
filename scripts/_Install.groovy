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

geronimoDefaultSettings {
	// The location of the geronimo installation
    this.'geronimo-home' = ''
    // The user name necessary for deploying to the server
    this.'geronimo-user' = 'system'
    // The password necessary for deploying to the server
    this.'geronimo-pass' = 'manager'
    // The temporary staging dir for deploying library jars
    this.'geronimo-staging-dir' = grailsSettings.projectWarExplodedDir.toString() + "_geronimo"
    // If true, cars/rars will not be generated during war build
    this.'no-geronimo-common-resource-packages' = false
    // If true, skinny war will not be built during war deployment
    this.'no-geronimo-war' = false
    // If true, cars will not be deployed during war deployment
    this.'no-geronimo-deploy-common-resource-packages' = false
    // If true, library jars will not be deployed during war deployment
    this.'no-geronimo-deploy-libs' = false
	// If true, grails tomcat plugin will not be deployed (not usually necessary for geronimo server)
	this.'no-geronimo-deploy-tomcat' = true
	// Options are 'gsh' to use gshell or 'native-nix' for deployer.sh
	this.'geronimo-deployer' = 'native-nix' 
    // The version of geronimo we support
    this.'geronimo-version' = '2.1.6'
	// If true, geronimo-web.xml will be generated, otherwise no geronimo-web.xml will be generated into the war
	this.'geronimo-build-geronimo-web-xml' = true
}

// Map from ivy artifacts to maven artifacts
ivyToMavenArtifactMap {
	this.'apache-taglibs:standard' = [ groupId : 'taglibs', artifactId : 'standard' ]
	this.'org.springframework:org.springframework.transaction' = [ groupId : 'org.springframework', artifactId : 'spring-tx' ]
	this.'org.springframework:org.springframework.web.servlet' = [ groupId : 'org.springframework', artifactId : 'spring-webmvc' ]
}

// Maven settings for generating car/rar files
mavenDefaultSettings {
    this.'maven-group-id' = 'org.apache.geronimo.plugins'
    this.'maven-base-dir' = 'target/geronimo'
    this.'maven-packaging' = 'rar'
	this.'maven-opts' = '' // Additional command line options to pass to maven build
}

// Maps a plugin name to a list of artifacts in groupId:artifactId:version:packaging format
additionalPluginDependencies {
	this.'grails-core' = [ 
		'org.hibernate:hibernate-core:3.3.1.GA:jar',
		'dom4j:dom4j:1.6.1:jar'
		]
}

"""

}

println "##########################################\nFor configuration options, see ${configFile.absolutePath}\n##########################################"

