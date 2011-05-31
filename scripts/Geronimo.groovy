import groovy.io.FileType

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
		def info = ivy.info
		this.groupId = info.@organisation
		this.artifactId = info.@module
		this.version = info.@revision
		// println ivy.publications.artifact
		this.packaging = ivy.publications.artifact.find { it.@conf in ['master', 'default'] }?.@type
	}
	
	String toString() {
		"groupId: $groupId, artifactId: $artifactId, version: $version, packaging: $packaging"
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
	println "listing dependencies"
	def dependencies = getDependencyIvyFileList()
	println dependencies
}

target(main: "The description of the script goes here!") {
 	depends(listDependencies)
}

setDefaultTarget(main)
