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

// Targets for deploying to Geronimo

target(deployCommonResourcePackages: "Deploys car plugins into geronimo server") {
    depends(parseArguments)

    if ( getConfigUtil().getGeronimoShouldGenerateCommonResourcePackages() )
        generateCommonResourcePackages()

    getGeronimoPackagingPolicy().doDeployCommonResourcePackages()
}

target(deployLibs: "Deploys local jar library files into geronimo server") {
    depends(parseArguments)

    // Create staging dir
    def geronimoStagingDir = getConfigUtil().getGeronimoStagingDir()
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
        execGshCmd( getConfigUtil().getGeronimoHome(), "deploy/install-library ${fileName} --groupId ${libDependency.groupId} -u ${getConfigUtil().getGeronimoUser()} -w ${getConfigUtil().getGeronimoPass()}" )
    }

    // Remove staging dir
    ant.delete(dir:"${geronimoStagingDir}", failonerror:true)
}

target(deployWar: "Deploys war into geronimo server") {
    depends(parseArguments)

    if ( getConfigUtil().getGeronimoShouldGenerateWar() ) {
        skinnyWar()
	}
    
    if ( getConfigUtil().getGeronimoShouldDeployCommonResourcePackages() ) {
        // Avoid building car files twice as they should already be built from war generation
        getGeronimoPackagingPolicy().doDeployCommonResourcePackages()
    }

    if ( getConfigUtil().getGeronimoShouldDeployLibs() && getGeronimoPackagingPolicy().shouldDeployLibs() )
        deployLibs()

    // Based on: https://cwiki.apache.org/GMOxDOC21/gshell.html#GShell-DeployinganApplicationtoaServerInstance
    def warPath = new File("target/${grailsAppName}-${metadata.getApplicationVersion()}.war").absolutePath
    execGshCmd( getConfigUtil().getGeronimoHome(), "deploy/deploy ${warPath} -u ${getConfigUtil().getGeronimoUser()} -w ${getConfigUtil().getGeronimoPass()}" )
}

