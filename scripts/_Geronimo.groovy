import groovy.xml.MarkupBuilder

// For extracting core dependencies
import grails.util.BuildSettings
import grails.util.Metadata
import org.codehaus.groovy.grails.resolve.IvyDependencyManager

includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsClean")
includeTargets << grailsScript("_GrailsPackage")

class Library {
	String groupId, artifactId, version, packaging
	File ivyFile
	File[] packages
	
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

getAppDependencyIvyFileList = {
	grailsSettings.runtimeDependencies.inject([]) { dependencies, jar ->
		def d = new Library(packages: [jar])
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

target(listCoreDependencies: "Display a list of core/default dependencies") {
    Metadata metadata = Metadata.current
    def appName = metadata.getApplicationName() ?: "grails"
    def appVersion = metadata.getApplicationVersion() ?: grailsSettings.grailsVersion

    BuildSettings dummyBuildSettings = new BuildSettings()
    IvyDependencyManager defaultDependencyManager = new IvyDependencyManager(appName, appVersion, dummyBuildSettings, metadata)                                   
   
    Closure defaultDependencies = defaultDependencyManager.getDefaultDependencies( grailsSettings.grailsVersion )
    defaultDependencyManager.parseDependencies( defaultDependencies )

    defaultDependencyManager.moduleDescriptor.getDependencies().each {
        println "${it.getModuleConfigurations()} -> ${it.getDependencyRevisionId().getName()}"
    }
}

target(listAppDependencies: "Display a list of Ivy dependencies for this Grails project") {
	println "Retrieving app dependencies"

    def dependencies = getAppDependencyIvyFileList()
	dependencies.each {
	    println "- $it"
	}
}

target(generateCore: "Generates Maven pom.xml files which can be packaged into Geronimo plugins") {
	println "Generating Grails Core Maven Project"
	
	new File('target/geronimo/grails-core/').mkdirs()
	def pomWriter = new FileWriter('target/geronimo/grails-core/pom.xml')
	generateCorePom(new MarkupBuilder(pomWriter))
	
	new File('target/geronimo/grails-core/src/main/plan').mkdirs()
	def planWriter = new FileWriter('target/geronimo/grails-core/src/main/plan/plan.xml')
	generateCorePlan(new MarkupBuilder(planWriter))
}

target(main: "The description of the script goes here!") {
 	depends(listDependencies, generateCore)
}

target(skinnyWar: "Generates a skinny war") {
    // TODO:
    println "You have called target: 'skinnyWar'."
}

target(generateCars: "Generates car files") {
    // TODO:
    println "You have called target: 'generateCars'."
}

setDefaultTarget(main)
