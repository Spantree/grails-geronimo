// Imports

import groovy.xml.MarkupBuilder
import grails.util.BuildSettings
import grails.util.Metadata
import org.codehaus.groovy.grails.resolve.IvyDependencyManager

// Includes

includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsClean")
includeTargets << grailsScript("_GrailsPackage")
includeTargets << grailsScript("_GrailsPlugins")

// Classes

// A class for storing info on a single dependency
class Dependency {

    // Stores the organisation that created the artifact
    String groupId
    // The 'artefact' id
    String artifactId
    // The version number
    String version
    // How this dependency is packaged (e.g - 'jar')
    String packaging
    // The ivy file specifying this dependency (may be null)
	File ivyFile
    // The packages [jar] files that make up this artifact (may be null)
	File[] packages

    
    // Extract dependency information from an Ivy file 	
	void setIvyFile(File ivyFile) {
		this.@ivyFile = ivyFile
		def ivy = new XmlParser().parse(ivyFile)
		def info = ivy.info[0]
		this.groupId = info.@organisation
		this.artifactId = info.@module
		this.version = info.@revision
		// println ivy.publications.artifact
		this.packaging = ivy.publications.artifact.find { it.@conf in ['master', 'default'] }?.@type
	}
	
	String toString() {
		"$groupId:$artifactId:$version:$packaging"
	}
	
	String getMavenDependencyElement() {
		def writer = new StringWriter()
		def xml = new MarkupBuilder(writer)
		xml.dependency() {
			groupId(this.groupId)
			artifactId(this.artifactId)
			version(this.version)
			type(this.packaging) 
		}
		writer.toString()
	}
}

// Globals

// This defines which configurations we're interested in
def runtimeConfigurations = ["runtime", "compile"]

// A list of core runtime dependencies
def coreDependencies

// A map from plugin name -> map [ modules:[list of runtime Dependency objects] libs:[jar names] ]
def pluginDependencies

// A list of runtime dependencies for this application
def appDependencies

// A list of all application dependencies minus core and plugin
def skinnyAppDependencies

// Utilities for extracting runtime dependencies

// Returns true if module configuration is found within our configurations list
boolean isAllowedConfiguration( def moduleConfigurations, def runtimeConfigurations )
{
    return (moduleConfigurations as ArrayList).any { 
        runtimeConfigurations.contains( it )
    }
}

// Extracts dependency information from a parameter Spring DependencyDescriptor
Dependency createDependencyFromDependencyDescriptor( def dd )
{
    def d = new Dependency()
    d.groupId = dd.dependencyRevisionId.organisation
    d.artifactId = dd.dependencyRevisionId.name
    d.version = dd.dependencyRevisionId.revision
    d.packaging = "jar"
    return d
}

// Performs A - B set operation
List getDependencySetDifference( def dependencyListA, def dependencyListB ) {
   dependencyListA.findAll {
        aDep -> !dependencyListB.any {
            bDep -> (bDep as String) == (aDep as String)    
        } 
    }
}

// Extracts grails core runtime dependencies
getCoreDependencies = {
    if ( !coreDependencies )
    {
        Metadata metadata = Metadata.current
        def appName = metadata.getApplicationName() ?: "grails"
        def appVersion = metadata.getApplicationVersion() ?: grailsSettings.grailsVersion

        BuildSettings dummyBuildSettings = new BuildSettings()
        IvyDependencyManager defaultDependencyManager = new IvyDependencyManager(appName, appVersion, dummyBuildSettings, metadata)                                   
       
        Closure defaultDependencies = defaultDependencyManager.getDefaultDependencies( grailsSettings.grailsVersion )
        defaultDependencyManager.parseDependencies( defaultDependencies )

        coreDependencies = []
        defaultDependencyManager.moduleDescriptor.dependencies.each {
            if ( isAllowedConfiguration( it.moduleConfigurations, runtimeConfigurations ) ) { 
                coreDependencies << createDependencyFromDependencyDescriptor( it )
            }
        }
    }
    return coreDependencies
}

// Extracts runtime dependencies for all installed plugins
getPluginDependencies = {
    if (!pluginDependencies)
    {
        Metadata metadata = Metadata.current
        def appName = metadata.getApplicationName() ?: "grails"
        def appVersion = metadata.getApplicationVersion() ?: grailsSettings.grailsVersion

        pluginDependencies = [:]

        pluginSettings.pluginInfos.each {
            IvyDependencyManager dependencyManager = new IvyDependencyManager( appName, appVersion, grailsSettings, metadata )
            dependencyManager.moduleDescriptor = dependencyManager.createModuleDescriptor()
            def callable = grailsSettings.pluginDependencyHandler( dependencyManager )
            callable.call(new File("${it.pluginDir.file.canonicalPath}"))

            def currentPluginName = it.name
            pluginDependencies[ currentPluginName ] = [ modules:[], libs:[] ] 
            
            dependencyManager.moduleDescriptor.dependencies.each {
                if ( isAllowedConfiguration( it.moduleConfigurations, runtimeConfigurations ) ) {
            	    pluginDependencies[ currentPluginName ].modules << createDependencyFromDependencyDescriptor( it )
                }
            }

            def pluginJars = new File("${it.pluginDir.file.canonicalPath}/lib").listFiles().findAll { it.name.endsWith(".jar")}
            pluginJars.each{
                pluginDependencies[ currentPluginName ].libs << it
            }
        }
    }
    return pluginDependencies
}

// Extracts application runtime information and associated Ivy files
getAppDependencyIvyFileList = {
    if ( !appDependencies ) {
        appDependencies = grailsSettings.runtimeDependencies.inject([]) { dependencies, jar ->
		    def d = new Dependency(packages: [jar])
		    def ivyBase = jar.parentFile.parentFile
		    ivyBase.eachFileMatch(~/^ivy-.*\.xml$/) { ivyFile ->
			    def version = (ivyFile.name =~ /^ivy-(.*)\.xml/)[0][1]
			    if(jar.name ==~ ".*${version}.*") {
				    d.ivyFile = ivyFile
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
        skinnyAppDependencies = getDependencySetDifference( getAppDependencyIvyFileList(), getCoreDependencies() )
      
        // Remove plugin dependencies
        getPluginDependencies().each {
                skinnyAppDependencies = getDependencySetDifference( skinnyAppDependencies, it.value.modules )               
        }
    }
    return skinnyAppDependencies
}

// Utilities for generating Pom and Plan files

generateCorePom = { xml ->
	xml.project('xsi:schemaLocation': 'http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd') {
		modelVersion('4.0.0')
		parent() {
			groupId('org.apache.geronimo.genesis.config')
			artifactId('project-config')
			version('1.5')
		}
		groupId('org.apache.geronimo.plugins')
		artifactId('grails-core')
		name('Geronimo Plugins :: Geronimo Grails Core Plugin')
		packaging('car')
		version(grailsVersion)
		properties() {
			geronimoVersion('2.2.1')
			projectName('Geronimo Plugins :: Geronimo Grails Core Plugin')
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
			getAppDependencyIvyFileList().each { dep ->
				dependency() {
					groupId(dep.groupId)
					artifactId(dep.artifactId)
					version(dep.version)
					type(dep.packaging)
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
					}
				}
			}
		}
	}
}

generateCorePlan = { xml ->
	xml.module {
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

// Targets for listing dependencies

target(listCoreDependencies: "Display a list of core/default dependencies") {
    depends(compile)
    println "Retrieving core dependencies"
	getCoreDependencies().each {
	    println "- $it"
	}
}

target(listPluginDependencies: "Display a list of dependencies for each plugin") {
    println "Retrieving plugin dependencies"
    getPluginDependencies().each {
        println "-------------------\nPLUGIN: $it.key\n-------------------"         
        it.value.modules.each {
            println "- $it"
        }
        it.value.libs.each {
            println "[LIB] - $it"
        }
	}
}

target(listAppDependencies: "Display a list of Ivy dependencies for this Grails project") {
	println "Retrieving app dependencies"
    getAppDependencyIvyFileList().each {
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

target(generateCore: "Generates Maven pom.xml files which can be packaged into Geronimo plugins") {
	println "Generating Grails Core Maven Project"
	
	new File('target/geronimo/grails-core/').mkdirs()
	def pomWriter = new FileWriter('target/geronimo/grails-core/pom.xml')
	generateCorePom(new MarkupBuilder(pomWriter))
	
	new File('target/geronimo/grails-core/src/main/plan').mkdirs()
	def planWriter = new FileWriter('target/geronimo/grails-core/src/main/plan/plan.xml')
	generateCorePlan(new MarkupBuilder(planWriter))
}

target(generateCars: "Generates car files") {
    // TODO:
    println "You have called target: 'generateCars'."
}

target(skinnyWar: "Generates a skinny war") {
    // TODO:
    println "You have called target: 'skinnyWar'."
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
