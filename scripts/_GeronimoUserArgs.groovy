includeTargets << new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy")

// Geronimo user args utilities

getGeronimoSetting = { key ->
    argsMap[ key ] ?: getGeronimoDefaultSettings()[ key ]
}

getGeronimoHome = {
    getGeronimoSetting( 'geronimo-home' )
}

getGeronimoUser = {
    getGeronimoSetting( 'geronimo-user' )
}

getGeronimoPass = {
    getGeronimoSetting( 'geronimo-pass' )
}

getGeronimoStagingDir = {
    getGeronimoSetting( 'geronimo-staging-dir' )
}

getGeronimoShouldGenerateCars = {
    return !getGeronimoSetting( 'no-geronimo-cars' )
}

getGeronimoShouldGenerateWar = {
    return !getGeronimoSetting( 'no-geronimo-war' )
}

getGeronimoShouldDeployCars = {
    return !getGeronimoSetting( 'no-geronimo-deploy-cars' )
}

getGeronimoShouldDeployLibs = {
    return !getGeronimoSetting( 'no-geronimo-deploy-libs' )
}

