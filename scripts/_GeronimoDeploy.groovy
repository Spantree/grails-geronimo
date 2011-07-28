includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoWar.groovy")

// Runs a GShell command
execGshCmd = { geronimoHome, cmd ->
    def commandList = ["${geronimoHome}/bin/gsh", "-c", "$cmd"]
    println "Executing $commandList"
    def proc = commandList.execute()
    proc.waitFor()
    System.out << proc.text
    println "return code: ${proc.exitValue()}"
    println "stderr: ${proc.err.text}"
}

// Does the actual work of deploying each individual car (does not build the cars)
def doDeployCars = {
   new File(getMavenSettings().baseDir).eachDir { File pluginBaseDir ->
        def pluginCarDir = new File( "${pluginBaseDir}/target" )        
        pluginCarDir.eachFileMatch(~/.*\.car/) {
            execGshCmd( getGeronimoHome(), "deploy/install-plugin ${it.absolutePath} -u ${getGeronimoUser()} -w ${getGeronimoPass()}" )
        }
    }
}

// Targets for deploying to Geronimo

target(deployCars: "Deploys car plugins into geronimo server") {
    depends(parseArguments)

    if ( getGeronimoShouldGenerateCars() )
        generateCars()

    doDeployCars()
}

target(deployLibs: "Deploys local jar library files into geronimo server") {
    depends(parseArguments)

    // Create staging dir
    def geronimoStagingDir = getGeronimoStagingDir()
    ant.mkdir(dir:geronimoStagingDir)
    
    getLibDependencies().each { libDependency ->
        // Stage dependency if necessary
        def fileName = libDependency.file.toString()        
        if ( libDependency.requiresStaging ) {
            stagedFileName = "${geronimoStagingDir}/${libDependency.packagedName}"
            ant.copy( file:fileName, tofile:stagedFileName  )
            fileName = stagedFileName
        }
        // Deploy library to geronimo, based on https://cwiki.apache.org/GMOxDOC22/deploy.html
        execGshCmd( getGeronimoHome(), "deploy/install-library ${fileName} --groupId ${libDependency.groupId} -u ${getGeronimoUser()} -w ${getGeronimoPass()}" )
    }

    // Remove staging dir
    ant.delete(dir:"${geronimoStagingDir}", failonerror:true)
}

target(deployWar: "Deploys war into geronimo server") {
    depends(parseArguments)

    if ( getGeronimoShouldGenerateWar() )
        skinnyWar()
    
    if ( getGeronimoShouldDeployCars() ) {
        // Avoid building car files twice as they should already be built from war generation
        doDeployCars()
    }

    if ( getGeronimoShouldDeployLibs() )
        deployLibs()

    // Based on: https://cwiki.apache.org/GMOxDOC21/gshell.html#GShell-DeployinganApplicationtoaServerInstance
    def warPath = new File("target/${grailsAppName}-${metadata.getApplicationVersion()}.war").absolutePath
    execGshCmd( getGeronimoHome(), "deploy/deploy ${warPath} -u ${getGeronimoUser()} -w ${getGeronimoPass()}" )
}

