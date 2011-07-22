// Imports

import groovy.xml.MarkupBuilder
import grails.util.BuildSettings
import grails.util.Metadata
import org.codehaus.groovy.grails.resolve.IvyDependencyManager
import org.codehaus.groovy.grails.commons.ConfigurationHolder as grailsConfigHolder

// Includes

includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsClean")
includeTargets << grailsScript("_GrailsPackage")
includeTargets << grailsScript("_GrailsPlugins")
includeTargets << grailsScript("_GrailsWar")

// Globals

// This defines which configurations we're interested in
def runtimeConfigurations = ["runtime", "compile"]

// The geronimo module for grails-core
def coreGeronimoModule

// The geronimo module for all plugins
// Note: module is in form
def pluginGeronimoModules

// A map from plugin name -> map [ modules:[list of runtime Dependency objects] libs:[jar names] ]
//def pluginDependencies

// A list of runtime dependencies for this application
def appDependencies

// A list of all application dependencies minus core and plugin
def skinnyAppDependencies

// Maps a key ("group:artifact") to maven group and artifact ids
// TODO: This probably needs to be set from a data file somewhere and the keys need to be ivy group and artifact names
def mappedMavenGroupAndArtifactIds = [
	"apache-taglibs:standard" : [ groupId : "taglibs", artifactId : "standard" ],
	"org.springframework:spring-transaction" : [ groupId : "org.springframework", artifactId : "spring-tx" ],
	"org.springframework:spring-web-servlet" : [ groupId : "org.springframework", artifactId : "spring-webmvc" ]
]

// Global maven settings
def mavenSettings = [
    geronimoVersion : '2.1.7',
    groupId : 'org.apache.geronimo.plugins',
    baseDir : 'target/geronimo',
    packaging : 'car'
]

// Classes

// Stores metadata on a module
class Module {
   // Stores the organisation that created the artifact
    String groupId
    // The 'artefact' id
    String artifactId
    // The version number
    String version
    // How this module is packaged (e.g - 'jar')
    String packaging

    String toString() {
        "$groupId:$artifactId:$version:$packaging"
    }

    int hashCode() {
        return this.toString().hashCode()
    }
}

// A high level geronimo war or plugin car with it's associated dependencies and libs
class GeronimoModule extends Module {
    // List of dependencies
    def dependencies
    // List of libraries included with this module
    def libs
    // Returns string description for maven pom
    String getMavenName() {
        "Geronimo Plugins :: Geronimo ${this.artifactId} Plugin"
    }
}

// A class for storing info on a single dependency
class DependencyModule extends Module {
    // Extract dependency information from an Ivy file     
    void setIvyFile(File ivyFile) {
        def ivy = new XmlParser().parse(ivyFile)
        def info = ivy.info[0]
        this.groupId = info.@organisation
        this.artifactId = info.@module
        this.version = info.@revision
        this.packaging = ivy.publications.artifact.find { it.@conf in ['master', 'default'] }?.@type
    }
    // Extracts dependency information from an Ivy/Spring Dependency Descriptor
    void setDependencyDescriptor(def dd) {
        this.groupId = dd.dependencyRevisionId.organisation
        this.artifactId = dd.dependencyRevisionId.name
        this.version = dd.dependencyRevisionId.revision
        this.packaging = "jar"
    }
    // Returns the name of jar file representing this dependency
    String getPackagedName() {
        "${artifactId}-${version}.${packaging}"
    }
    // Returns Maven mapped group and artifact identifiers
    // TODO: This should use non-processed keys!
    Map getMavenGroupAndArtifactIds( def mappedMavenGroupAndArtifactIds ) {
        def mavenProcessedArtifactId = this.artifactId.replaceAll( /^org\.springframework\./, "spring-" ).replaceAll( /\./, "-" )
        def mavenKey = this.groupId + ":" + mavenProcessedArtifactId
        return mappedMavenGroupAndArtifactIds[ mavenKey ] ?: [ groupId : this.groupId, artifactId : mavenProcessedArtifactId ]
    }
}

// Utilities for extracting runtime dependencies

// Returns true if module configuration is found within our configurations list
boolean isAllowedConfiguration( def moduleConfigurations, def runtimeConfigurations ) {
    return (moduleConfigurations as ArrayList).any { 
        runtimeConfigurations.contains( it )
    }
}

// Initializes the grails-core geronimo module
initCoreGeronimoModule = {
    // Initialize module metadata
    coreGeronimoModule = new GeronimoModule(
        groupId : mavenSettings.groupId,    
        artifactId : 'grails-core',
        version : grailsSettings.grailsVersion,  
        packaging : mavenSettings.packaging,
        dependencies : [],
        libs : []
    )

    // Determine module dependencies
    Metadata metadata = Metadata.current
    def appName = metadata.getApplicationName() ?: "grails"
    def appVersion = metadata.getApplicationVersion() ?: grailsSettings.grailsVersion

    BuildSettings dummyBuildSettings = new BuildSettings()
    IvyDependencyManager defaultDependencyManager = new IvyDependencyManager(appName, appVersion, dummyBuildSettings, metadata)                                   
   
    Closure defaultDependencies = defaultDependencyManager.getDefaultDependencies( grailsSettings.grailsVersion )
    defaultDependencyManager.parseDependencies( defaultDependencies )

    defaultDependencyManager.moduleDescriptor.dependencies.each {
        if ( isAllowedConfiguration( it.moduleConfigurations, runtimeConfigurations ) ) { 
            coreGeronimoModule.dependencies << new DependencyModule( dependencyDescriptor : it )
        }
    }
}

// Returns data structure with all information necessary to generate and deploy the core geronimo car
getCoreGeronimoModule = {
   if ( !coreGeronimoModule )
        initCoreGeronimoModule()
    return coreGeronimoModule
}

// Initializes a map from plugin name -> plugin geronimo module
initPluginGeronimoModules = {
    Metadata metadata = Metadata.current
    def appName = metadata.applicationName ?: "grails"
    def appVersion = metadata.applicationVersion ?: grailsSettings.grailsVersion

    pluginGeronimoModules = []

    pluginSettings.pluginInfos.each {
        IvyDependencyManager dependencyManager = new IvyDependencyManager( appName, appVersion, grailsSettings, metadata )
        dependencyManager.moduleDescriptor = dependencyManager.createModuleDescriptor()
        def callable = grailsSettings.pluginDependencyHandler( dependencyManager )
        callable.call(new File("${it.pluginDir.file.canonicalPath}"))

        def pluginInfo = it
        pluginGeronimoModule = new GeronimoModule(
            groupId : mavenSettings.groupId,
            artifactId : "grails-${pluginInfo.name}",
            version : "${pluginInfo.version}",
            packaging : mavenSettings.packaging,
            dependencies : [],
            libs : []
        )

        dependencyManager.moduleDescriptor.dependencies.each {
            if ( isAllowedConfiguration( it.moduleConfigurations, runtimeConfigurations ) ) {
                pluginGeronimoModule.dependencies << new DependencyModule( dependencyDescriptor : it )
            }
        }

        def pluginJars = new File("${it.pluginDir.file.canonicalPath}/lib").listFiles().findAll { it.name.endsWith(".jar") }
        pluginJars.each {
            pluginGeronimoModule.libs << it
        }

        pluginGeronimoModules << pluginGeronimoModule
    }
}

// Extracts runtime dependencies for all installed plugins
getPluginGeronimoModules = {
    if (!pluginGeronimoModules)
        initPluginGeronimoModules()
    return pluginGeronimoModules
}

// Extracts application runtime information
getAppDependencies = {
    if ( !appDependencies ) {
        appDependencies = grailsSettings.runtimeDependencies.inject([]) { dependencies, jar ->
            def d = new DependencyModule()
            def ivyBase = jar.parentFile.parentFile
            ivyBase.eachFileMatch(~/^ivy-.*\.xml$/) { ivyFile ->
                def version = (ivyFile.name =~ /^ivy-(.*)\.xml/)[0][1]
                if(jar.name ==~ ".*${version}.*") {
                    d.setIvyFile( ivyFile )
                }
            }
            dependencies << d
        }
    }
    return appDependencies
}

getSkinnyAppDependencies = {
    if ( !skinnyAppDependencies ) {
        // Remove core dependencies
        skinnyAppDependencies = getAppDependencies() - getCoreGeronimoModule().dependencies
      
        // Remove plugin dependencies
        getPluginGeronimoModules().each {
            skinnyAppDependencies = skinnyAppDependencies - it.dependencies              
        }
    }
    return skinnyAppDependencies
}

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
void generateGeronimoWebXml( def args ) {
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
                            groupId( dep.getMavenGroupAndArtifactIds( args.mappedMavenGroupAndArtifactIds ).groupId )
                            artifactId( dep.getMavenGroupAndArtifactIds( args.mappedMavenGroupAndArtifactIds ).artifactId )
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

// Populates a pom xml file for building a car
// Takes an arguments map = [ 
//      xml:(markupBuilder), - the markup builder used for generating the xml
//      mappedMavenGroupAndArtifactIds:(map) - maps ivy group and artifact ids to maven ids
//      geronimoVersion:(string) - the version of geronimo to use
//      geronimoModule: - a module containing metadata such as artifact id, group id, and dependencies
void generatePomXml( def args ) {
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
                    groupId( dep.getMavenGroupAndArtifactIds( args.mappedMavenGroupAndArtifactIds ).groupId )
                    artifactId( dep.getMavenGroupAndArtifactIds( args.mappedMavenGroupAndArtifactIds ).artifactId )
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

void generatePlanXml( def xml ) {
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

void generatePomAndPlanXml( def mappedMavenGroupAndArtifactIds, def mavenSettings, def geronimoModule ) {
    def artifactRootPath = "${mavenSettings.baseDir}/${geronimoModule.artifactId}"
    new File( "$artifactRootPath/" ).mkdirs()
    def pomWriter = new FileWriter("$artifactRootPath/pom.xml")
    generatePomXml( 
        [ xml : (new MarkupBuilder(pomWriter)), 
          mappedMavenGroupAndArtifactIds : mappedMavenGroupAndArtifactIds,
          geronimoVersion : mavenSettings.geronimoVersion, 
          geronimoModule : geronimoModule ]
    )
    
    new File("$artifactRootPath/src/main/plan/").mkdirs()
    def planWriter = new FileWriter("$artifactRootPath/src/main/plan/plan.xml")
    generatePlanXml(new MarkupBuilder(planWriter))
}

// War utilities

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
        xmlParams.mappedMavenGroupAndArtifactIds = mappedMavenGroupAndArtifactIds
    }

    return xmlParams
}

// Returns the default manifest file name for use in war generation
getDefaultManifestFileName = {
    return "$stagingDir/META-INF/MANIFEST.MF"
}

// Generates a non-archived war in a staging directory
generateExplodedWar = { 
    // Flag that we should not delete the war staging directory
    buildExplodedWar = true
    // Build the exploded war (does not actually generate a war file)
    war()
}

// Collects war staging directory into a single war archive file
generateWarArchive = { manifestFile ->
    // Create the war file
    def warFile = new File(warName)
    def dir = warFile.parentFile
    if (!dir.exists()) ant.mkdir(dir:dir)
        ant.jar(destfile:warName, basedir:stagingDir, manifest:manifestFile?:getDefaultManifestFileName())
}

// Targets for listing dependencies

target(listCoreDependencies: "Display a list of core/default dependencies") {
    depends(compile)
    println "Retrieving core dependencies"
    getCoreGeronimoModule().dependencies.each {
        println "- $it"
    }
}

target(listPluginDependencies: "Display a list of dependencies for each plugin") {
    println "Retrieving plugin dependencies"
    getPluginGeronimoModules().each {
        println "-------------------\nPLUGIN: ${it.artifactId}\n-------------------"         
        it.dependencies.each {
            println "- $it"
        }
        it.libs.each {
            println "[LIB] - $it"
        }
    }
}

target(listAppDependencies: "Display a list of Ivy dependencies for this Grails project") {
    println "Retrieving app dependencies"
    getAppDependencies().each {
        println "- $it"
    }
}

target(listSkinnyAppDependencies: "Display a list of dependencies for the skinny war") {
    println "Retrieving skinny app dependencies"
    getSkinnyAppDependencies().each {
        println "- $it"
    }
}

// Targets for building skinny wars

target(stageCore: "Generates Maven pom.xml and plan.xml files for grails-core which can be packaged into Geronimo plugins") {
    println "Generating Maven XML for grails-core"
    generatePomAndPlanXml(
        mappedMavenGroupAndArtifactIds,
        mavenSettings,
        getCoreGeronimoModule()
    )
}

target(stagePlugins: "Generates a Maven pom.xml and plan.xml for each installed plugin") {
    getPluginGeronimoModules().each {
        println "Generating Maven XML for ${it.artifactId}"
        generatePomAndPlanXml(
            mappedMavenGroupAndArtifactIds,
            mavenSettings,
            it
        )
    }
}

target(generateCars: "Generates car files") {
    depends(stageCore, stagePlugins)
    new File(mavenSettings.baseDir).eachDir { File pomBase ->
        println "Packaging car from ${pomBase}/pom.xml"
        def proc = "mvn package".execute([], pomBase)
        System.out << proc.text
        proc.waitFor()
    }
}

target(fatWar: "Generates a fat war suitable for geronimo deployment") {   
    generateExplodedWar()
    generateGeronimoWebXml( getDefaultGeronimoWebXmlParams() )
    generateWarArchive()
    cleanUpAfterWar()       
}

target(skinnyWar: "Generates a skinny war") {
    depends(generateCars)
    
    generateExplodedWar()

    // Extract jars which are core or plugin dependencies
    def jarsToDelete = []
    def libDir = new File("${stagingDir}/WEB-INF/lib")
    // Iterate over all jar files within lib dir
    libDir.eachFileMatch(~/.*\.jar/) {
        // Determine if jar file is a core or plugin dependency
        def jarName = it.name
        def isCoreDep = getCoreGeronimoModule().dependencies.any { it.packagedName == jarName }
        def isPluginDep =  isCoreDep ? false : getPluginGeronimoModules().any { plugin ->
               plugin.dependencies.any { it.packagedName == jarName } || plugin.libs.any { it.name == jarName }
        }

        if ( isCoreDep || isPluginDep )
            jarsToDelete << "${libDir}/${jarName}"
    }

    // Delete all jars that are core or plugin dependencies
    jarsToDelete.each {
        ant.delete(file:"${it}", failonerror:true)
    }

    // Update the bundled jars classpath in the manifest file
    String manifestFile = getDefaultManifestFileName()
    def classPathEntries = [ ".", "WEB-INF/classes" ]
    libDir.eachFileMatch(~/.*\.jar/) { classPathEntries << "WEB-INF/lib/${it.name}" }
    def classPath = classPathEntries.join(',')
    ant.manifest(file:manifestFile, mode:'update') {
        attribute(name:"Bundle-ClassPath",value:"${classPath}")
    }

    // Generate geronimo-web.xml    
    generateGeronimoWebXml( getDefaultGeronimoWebXmlParams( getAppDependencies() ) )

    // Create the war file
    generateWarArchive( manifestFile )

    // Remove staging dir
    cleanUpAfterWar()
}

// Scratch/Debug targets

target(debugTest: "Test code goes here") {
    // TODO:
}

// Main target

target(main: "The description of the script goes here!") {
     depends(listDependencies, generateCore)
}

setDefaultTarget(main)
