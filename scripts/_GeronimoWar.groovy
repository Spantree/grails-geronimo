includeTargets << grailsScript("_GrailsWar")

includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoModules.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoXml.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoUserArgs.groovy")

// War utilities

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

// Targets for building skinny wars

target(stageCore: "Generates Maven pom.xml and plan.xml files for grails-core which can be packaged into Geronimo plugins") {
    println "Generating Maven XML for grails-core"
    generatePomAndPlanXml( getCoreGeronimoModule() )
}

target(stagePlugins: "Generates a Maven pom.xml and plan.xml for each installed plugin") {
    getPluginGeronimoModules().each {
        println "Generating Maven XML for ${it.artifactId}"
        generatePomAndPlanXml( it )
    }
}

target(generateCars: "Generates car files") {
    depends(stageCore, stagePlugins)
    new File(getMavenSettings().baseDir).eachDir { File pomBase ->
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
    depends(parseArguments)

    if ( getGeronimoShouldGenerateCars() )
        generateCars()
    
    generateExplodedWar()

    // Extract jars which are core or plugin dependencies
    def jarsToDelete = []
    def libDir = new File("${stagingDir}/WEB-INF/lib")
    def providedModules = getPluginGeronimoModules() + getCoreGeronimoModule()
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
    generateGeronimoWebXml( getDefaultGeronimoWebXmlParams( getAppDependencies() + getLibDependencies() ) )

    // Create the war file
    generateWarArchive( manifestFile )

    // Remove staging dir
    cleanUpAfterWar()
}

