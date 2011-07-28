// Imports

import grails.util.BuildSettings
import grails.util.Metadata
import org.codehaus.groovy.grails.resolve.IvyDependencyManager
import org.apache.commons.io.FilenameUtils

// Includes

includeTargets << grailsScript("_GrailsPlugins")
includeTargets << new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy")

// Globals

// This defines which configurations we're interested in
def runtimeConfigurations = ["runtime", "compile"]

// The geronimo module for grails-core
def coreGeronimoModuleCache

// The geronimo modules for all plugins
def pluginGeronimoModulesCache

// A list of runtime dependencies for this application
def appDependenciesCache

// A list of all application dependencies minus core and plugin
def skinnyAppDependenciesCache

// A list of local library (jar) dependencies
def libDependenciesCache

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

    // Compact string representation
    String toString() {
        "$groupId:$artifactId:$version:$packaging"
    }

    // Hash code to allow for set operations
    int hashCode() {
        return this.toString().hashCode()
    }

    // Returns the name of jar file representing this dependency
    String getPackagedName() {
        "${artifactId}-${version}.${packaging}"
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

    // Returns Maven mapped group and artifact identifiers
    Map getMavenGroupAndArtifactIds( def ivyToMavenArtifactMap ) {
        return ivyToMavenArtifactMap[ this.groupId + ":" + this.artifactId ] ?: [
            groupId : this.groupId,
            artifactId: this.artifactId.replaceAll( /^org\.springframework\./, "spring-" ).replaceAll( /\./, "-" )
        ]
    }
}

// A class for storing info on a local jar/lib dependency
class LibModule extends Module {
    // A jar file handle
    def file
    // true if the library module requires staging before geronimo deployment, false otherwise
    def requiresStaging

    // Initializes the module for a map containing an owning geronimo module (owner key) and a jar file handle (file key)
    void setLibDescriptor(def libDescriptor) {
        // Set group id to owning module artifact id
        this.groupId = libDescriptor.owner.artifactId
        
        // Use the pattern from geronimo to extract artifactId, version, and packaging
        // From http://www.jarvana.com/jarvana/view/org/apache/geronimo/framework/geronimo-plugin/2.1.7/geronimo-plugin-2.1.7-javadoc.jar!/src-html/org/apache/geronimo/system/plugin/PluginInstallerGBean.html#line.130        
        def pattern = /(.+)-([0-9].+)\.([^0-9]+)/
        def nameNoPath = FilenameUtils.getName(libDescriptor.file.toString())
        def matcher = ( nameNoPath =~ pattern )
        
        // If the pattern fails, then the jar must be staged in order to match the pattern during geronimo deployment (else deployment will fail)
        this.requiresStaging = !(matcher.matches())
        if ( this.requiresStaging ) {
            this.artifactId = FilenameUtils.removeExtension( nameNoPath )
            // Set version to owning module version
            this.version = libDescriptor.owner.version            
            this.packaging = FilenameUtils.getExtension( nameNoPath )
        }
        else {
            this.artifactId = matcher[0][1]
            this.version = matcher[0][2]
            this.packaging = matcher[0][3]
        }
        this.file = libDescriptor.file
    }

    // HACK - these dependencies are not actually used by maven (but needed for geronimo-web.xml dependencies)
    Map getMavenGroupAndArtifactIds( def ivyToMavenArtifactMap ) {
        return [ groupId : this.groupId, artifactId : this.artifactId ]
    }

    // Return a string version of this dependency 
    String toString() {
        return super.toString() + "\n\t${file}"
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
    coreGeronimoModuleCache = new GeronimoModule(
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
            coreGeronimoModuleCache.dependencies << new DependencyModule( dependencyDescriptor : it )
        }
    }
}

// Returns data structure with all information necessary to generate and deploy the core geronimo car
getCoreGeronimoModule = {
   if ( !coreGeronimoModuleCache )
        initCoreGeronimoModule()
    return coreGeronimoModuleCache
}

// Initializes a map from plugin name -> plugin geronimo module
initPluginGeronimoModules = {
    Metadata metadata = Metadata.current
    def appName = metadata.applicationName ?: "grails"
    def appVersion = metadata.applicationVersion ?: grailsSettings.grailsVersion

    pluginGeronimoModulesCache = []

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
            pluginGeronimoModule.libs << new LibModule( libDescriptor : [ owner: pluginGeronimoModule, file: it ] )
        }

        pluginGeronimoModulesCache << pluginGeronimoModule
    }
}

// Extracts runtime dependencies for all installed plugins
getPluginGeronimoModules = {
    if (!pluginGeronimoModulesCache)
        initPluginGeronimoModules()
    return pluginGeronimoModulesCache
}

// Extracts application runtime information
getAppDependencies = {
    if ( !appDependenciesCache ) {
        appDependenciesCache = grailsSettings.runtimeDependencies.inject([]) { dependencies, jar ->
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
    return appDependenciesCache
}

getSkinnyAppDependencies = {
    if ( !skinnyAppDependenciesCache ) {
        // Remove core dependencies
        skinnyAppDependenciesCache = getAppDependencies() - getCoreGeronimoModule().dependencies
      
        // Remove plugin dependencies
        getPluginGeronimoModules().each {
            skinnyAppDependenciesCache = skinnyAppDependenciesCache - it.dependencies              
        }
    }
    return skinnyAppDependenciesCache
}

getLibDependencies = {
    if ( !libDependenciesCache ) {
        libDependenciesCache = []
        getPluginGeronimoModules().each { pluginModule ->
            libDependenciesCache.addAll( pluginModule.libs )
        }
    }
    return libDependenciesCache
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

