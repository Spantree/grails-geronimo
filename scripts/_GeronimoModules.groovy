// Imports

import grails.util.BuildSettings
import grails.util.Metadata
import org.codehaus.groovy.grails.resolve.IvyDependencyManager

// Includes

includeTargets << grailsScript("_GrailsPlugins")
includeTargets << new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy")

// Globals

// This defines which configurations we're interested in
def runtimeConfigurations = ["runtime", "compile"]

// The geronimo module for grails-core
def coreGeronimoModule

// The geronimo modules for all plugins
def pluginGeronimoModules

// A list of runtime dependencies for this application
def appDependencies

// A list of all application dependencies minus core and plugin
def skinnyAppDependencies

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
    Map getMavenGroupAndArtifactIds( def ivyToMavenArtifactMap ) {
        return ivyToMavenArtifactMap[ this.groupId + ":" + this.artifactId ] ?: [
            groupId : this.groupId,
            artifactId: this.artifactId.replaceAll( /^org\.springframework\./, "spring-" ).replaceAll( /\./, "-" )
        ]
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
        groupId : getMavenSettings().groupId,    
        artifactId : 'grails-core',
        version : grailsSettings.grailsVersion,  
        packaging : getMavenSettings().packaging,
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
            groupId : getMavenSettings().groupId,
            artifactId : "grails-${pluginInfo.name}",
            version : "${pluginInfo.version}",
            packaging : getMavenSettings().packaging,
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

// Scratch/Debug targets

target(debugTest: "Test code goes here") {
    // TODO:
}

// Main target

target(main: "The description of the script goes here!") {
     depends(listAppDependencies)
}

setDefaultTarget(main)

