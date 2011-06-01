import groovy.xml.MarkupBuilder

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

getDependencyIvyFileList = {
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

target(listDependencies: "Display a list of Ivy dependencies for this Grails project") {
	println "Retrieving runtime dependencies"
	def dependencies = getDependencyIvyFileList()
	dependencies.each {
		println "- $it"
	}
}

target(generateGeronimoPlugins: "Generates Maven pom.xml files which can be packaged into Geronimo plugins") {
	println "Retrieving runtime dependencies"
	def writer = new StringWriter()
	def xml = new MarkupBuilder(writer)
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
			getDependencyIvyFileList().each { dep ->
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
	println writer.toString()
}

target(main: "The description of the script goes here!") {
 	depends(listDependencies, generateGeronimoPlugins)
}

setDefaultTarget(main)
