includeTargets << new File("${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy")

// Geronimo user args utilities

getGeronimoHome = {
    argsMap['geronimo-home'] ?: getGeronimoSettings().home
}

getGeronimoUser = {
    argsMap['geronimo-user'] ?: getGeronimoSettings().user
}

getGeronimoPass = {
    argsMap['geronimo-pass'] ?: getGeronimoSettings().pass
}

getGeronimoStagingDir = {
    argsMap['geronimo-staging-dir'] ?: grailsSettings.projectWarExplodedDir.toString() + "_geronimo"
}

getGeronimoShouldGenerateCars = {
    return !argsMap['no-geronimo-cars']
}

getGeronimoShouldGenerateWar = {
    return !argsMap['no-geronimo-war']
}

getGeronimoShouldDeployCars = {
    return !argsMap['no-geronimo-deploy-cars']
}

getGeronimoShouldDeployLibs = {
    return !argsMap['no-geronimo-deploy-libs']
}

