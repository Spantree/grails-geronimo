includeTargets << grailsScript("_GrailsWar")

includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoUserArgs.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoModules.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoXml.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoPackagingPolicies.groovy")

// War utilities

// Returns the default manifest file name for use in war generation
def getDefaultManifestFileName = {
    return "$stagingDir/META-INF/MANIFEST.MF"
}

// Generates a non-archived war in a staging directory
def generateExplodedWar = { 
    // Flag that we should not delete the war staging directory
    buildExplodedWar = true
    // Build the exploded war (does not actually generate a war file)
    war()
}

// Collects war staging directory into a single war archive file
def generateWarArchive = { manifestFile ->
    // Create the war file
    def warFile = new File(warName)
    def dir = warFile.parentFile
    if (!dir.exists()) ant.mkdir(dir:dir)
        ant.jar(destfile:warName, basedir:stagingDir, manifest:manifestFile?:getDefaultManifestFileName())
}

// Targets for building external dependency packages

target(stageCore: "Generates Maven pom.xml and plan.xml files for grails-core which can be packaged into Geronimo plugins") {
    println "Generating Maven XML for grails-core with result: ${conditionalCreateMavenXmlFiles( getCoreGeronimoModule(), getGeronimoPackagingPolicy() )}"
}

target(stagePlugins: "Generates a Maven pom.xml and plan.xml for each installed plugin") {
    getPluginGeronimoModules().each {
		println "Generating Maven XML for ${it.artifactId} with result: ${conditionalCreateMavenXmlFiles( it, getGeronimoPackagingPolicy() )}"
    }
}

target(generateCommonResourcePackages: "Generates maven package files for common resources") {
    depends(stageCore, stagePlugins, parseArguments)
    new File(getConfigUtil().getMavenBaseDir()).eachDir { File pomBase ->
        println "Packaging ${getConfigUtil().getMavenPackaging()} from ${pomBase}/pom.xml with options: ${getConfigUtil().getMavenOpts()}"
        def proc = "mvn package ${getConfigUtil().getMavenOpts()}".execute([], pomBase)
        System.out << proc.text
        proc.waitFor()
    }
}

// Targets for building wars

target(fatWar: "Generates a fat war suitable for geronimo deployment") {   
    generateExplodedWar()
    buildGeronimoWebXml( getDefaultGeronimoWebXmlParams() )
    generateWarArchive()
    cleanUpAfterWar()       
}

target(skinnyWar: "Generates a skinny war") {
    depends(parseArguments)

    if ( getConfigUtil().getGeronimoShouldGenerateCommonResourcePackages() )
        generateCommonResourcePackages()
    
    generateExplodedWar()

    // Extract jars which are core or plugin dependencies
    def jarsToDelete = []
    def libDir = new File("${stagingDir}/WEB-INF/lib")
    def providedModules = getProvidedModules()
    def providedJars = [ providedModules*.dependencies*.packagedName, providedModules*.libs*.file*.name ].flatten()

    // Iterate over all jar files within lib dir
    libDir.eachFileMatch(~/.*\.jar/) { jarFile ->
        if ( jarFile.name in providedJars )
            jarsToDelete << jarFile
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
    buildGeronimoWebXml( getDefaultGeronimoWebXmlParams( getGeronimoPackagingPolicy().getExternalDependencies() ) )

    // Create the war file
    generateWarArchive( manifestFile )

    // Remove staging dir
    cleanUpAfterWar()
}

