includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoWar.groovy")

// Utilties check for null user and pass and return default if null, else return original value

safeUser = { user -> return user ?: getConfigUtil().getGeronimoUser() }

safePass = { pass -> return pass ?: getConfigUtil().getGeronimoPass() }

// Shell exec utilities

// Runs a GShell command
gshExecCmd = { geronimoHome, cmd ->
    def commandList = ["${geronimoHome}/bin/gsh", "-c", "$cmd"]
    println "Executing $commandList"
    def proc = commandList.execute()
    proc.waitFor()
    System.out << proc.text
    println "return code: ${proc.exitValue()}"
    println "stderr: ${proc.err.text}"
}

// Runs a deploy.sh command
deployerExecCmd = { geronimoHome, cmd ->
	def command = "${geronimoHome}/bin/deploy.sh $cmd"
    println "Executing ${command}"
    def proc = command.execute()
    proc.waitFor()
    System.out << proc.text
    println "return code: ${proc.exitValue()}"
    println "stderr: ${proc.err.text}"	
}

// Deployment policy maps

// GShell deployment policy
gshDeployPolicyMap = [

	// Installs a library via GShell
	installLibrary : { fileName, groupId, user=null, pass=null ->
		// Deploy library to geronimo, based on https://cwiki.apache.org/GMOxDOC22/deploy.html
	    gshExecCmd( getConfigUtil().getGeronimoHome(), "deploy/install-library ${fileName} --groupId ${groupId} -u ${safeUser(user)} -w ${safePass(pass)}" )
	},
	
	// Installs a plugin via GShell
	installPlugin : { pluginPath, user=null, pass=null ->
		gshExecCmd( getConfigUtil().getGeronimoHome(), "deploy/install-plugin ${pluginPath} -u ${safeUser(user)} -w ${safePass(pass)}" )
	},
	
	// Deploys a module via GShell
	deployModule : { warPath, user=null, pass=null ->
		// Based on: https://cwiki.apache.org/GMOxDOC21/gshell.html#GShell-DeployinganApplicationtoaServerInstance
		gshExecCmd( getConfigUtil().getGeronimoHome(), "deploy/deploy ${warPath} -u ${safeUser(user)} -w ${safePass(pass)}" )
	}
]

// Deploy.sh deployment policy
deployerDeployPolicyMap = [

	// Installs a library via deploy shell script
	installLibrary : { fileName, groupId, user=null, pass=null ->
		deployerExecCmd( getConfigUtil().getGeronimoHome(), "-u ${safeUser(user)} -p ${safePass(pass)} install-library --groupId ${groupId} ${fileName}" )
	},
	
	// Installs a plugin via deploy shell script
	installPlugin : { pluginPath, user=null, pass=null ->
		deployerExecCmd( getConfigUtil().getGeronimoHome(), "-u ${safeUser(user)} -p ${safePass(pass)} install-plugin ${pluginPath}" )
	},
	
	// Deploys a module via deploy shell script
	deployModule : { warPath, user=null, pass=null ->
		deployerExecCmd( getConfigUtil().getGeronimoHome(), "-u ${safeUser(user)} -p ${safePass(pass)} deploy ${warPath}" )
	}
]

// Return our deployment policy
getGeronimoDeploymentPolicy = { deployerDeployPolicyMap }

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
		getGeronimoDeploymentPolicy().installLibrary( fileName, libDependency.groupId )
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
	getGeronimoDeploymentPolicy().deployModule( warPath )
}
