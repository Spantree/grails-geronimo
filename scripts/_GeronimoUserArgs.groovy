includeTargets << new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy")

// Geronimo user args utilities

def getConfigSetting = { key, configMap ->
	System.getProperty( key ) ?: configMap[ key ]
}

def getGeronimoSetting = { key ->
	getConfigSetting( key,  getGeronimoDefaultSettings() )
}

def getMavenSetting = { key ->
	getConfigSetting( key, getMavenDefaultSettings() )
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

	getGeronimoVersion : {
		getGeronimoSetting( 'geronimo-version' )
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
