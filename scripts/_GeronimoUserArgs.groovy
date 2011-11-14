
def config = new ConfigSlurper().parse( new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy").toURL() )

// Geronimo user args utilities

def getConfigSetting = { key, settingsMap ->
	System.getProperty( key ) ?: config."${settingsMap}"."${key}"
}

def getGeronimoSetting = { key ->
	getConfigSetting( key, 'geronimoDefaultSettings' )
}

def getMavenSetting = { key ->
	getConfigSetting( key, 'mavenDefaultSettings' )
}

def configUtilMap = [
		
	// Geronimo settings

	getGeronimoHome : {
	    getGeronimoSetting( 'geronimo-home' )
	},

	getGeronimoUser : {
	    getGeronimoSetting( 'geronimo-user' )
	},

	getGeronimoPass : {
	    getGeronimoSetting( 'geronimo-pass' )
	},

	getGeronimoStagingDir : {
	    getGeronimoSetting( 'geronimo-staging-dir' )
	},

	getGeronimoShouldGenerateCommonResourcePackages : {
	   !getGeronimoSetting( 'no-geronimo-common-resource-packages' )
	},

	getGeronimoShouldGenerateWar : {
	    !getGeronimoSetting( 'no-geronimo-war' )
	},

	getGeronimoShouldDeployCommonResourcePackages : {
	    !getGeronimoSetting( 'no-geronimo-deploy-common-resource-packages' )
	},

	getGeronimoShouldDeployLibs : {
	    !getGeronimoSetting( 'no-geronimo-deploy-libs' )
	},
	
	getGeronimoShouldDeployTomcat : {
		!getGeronimoSetting( 'no-geronimo-deploy-tomcat' )
	},

	getGeronimoDeployer : {
		getGeronimoSetting( 'geronimo-deployer' )
	},

	getGeronimoVersion : {
		getGeronimoSetting( 'geronimo-version' )
	},
	
	// Maven settings

	getMavenGroupId : {
		getMavenSetting( 'maven-group-id' )
	},

	getMavenBaseDir : {
		getMavenSetting( 'maven-base-dir' )
	},

	getMavenPackaging : {
		getMavenSetting( 'maven-packaging' )
	},

	getMavenOpts : {
		getMavenSetting( 'maven-opts' )
	}
]

getConfigUtil = { configUtilMap }

getIvyToMavenArtifactMap = { config.ivyToMavenArtifactMap }

getAdditionalPluginDependencies = { config.additionalPluginDependencies }
