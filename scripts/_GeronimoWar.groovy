includeTargets << new File(geronimoPluginDir, "scripts/_Geronimo.groovy")

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

target(deployCars: "Deploys car plugins into local geronimo server") {
    depends(parseArguments)

    if ( !argsMap."local-geronimo-home" ) {
        println "error: missing non-optional arguments.\nusage: grails local-depoloy-cars -local-geronimo-home=<path> [-geronimo-u=<user>] [-geronimo-w=<pass>] [-no-geronimo-cars]"
        return    
    }

    if ( !argsMap."no-geronimo-cars" )
        generateCars()

    def user = argsMap."geronimo-u" ?: "system"
    def pass = argsMap."geronimo-w" ?: "manager"

    new File(getMavenSettings().baseDir).eachDir { File pluginBaseDir ->
        def pluginCarDir = new File( "${pluginBaseDir}/target" )        
        pluginCarDir.eachFileMatch(~/.*\.car/) {
            def commandList = ["${argsMap.'local-geronimo-home'}/bin/gsh", "-c", "deploy/install-plugin ${it.absolutePath} -u $user -w $pass"]
            println "Executing $commandList"
            def proc = commandList.execute()
            proc.waitFor()
            System.out << proc.text            
            println "return code: ${proc.exitValue()}"
            println "stderr: ${proc.err.text}"
        }
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

    if ( !argsMap."no-geronimo-cars" )
        generateCars()
    
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

