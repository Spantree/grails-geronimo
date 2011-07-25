includeTargets << new File(geronimoPluginDir, "scripts/_Geronimo.groovy")

// Targets for listing dependencies

target(listCoreDependencies: "Display a list of core/default dependencies") {
    println "Retrieving core dependencies"
    getCoreGeronimoModule().dependencies.each {
        println "- $it"
    }
}

target(listPluginDependencies: "Display a list of dependencies for each plugin") {
    println "Retrieving plugin dependencies"
    getPluginGeronimoModules().each {
        println "-------------------\nPLUGIN: ${it.artifactId}\n-------------------"         
        it.dependencies.each {
            println "- $it"
        }
        it.libs.each {
            println "[LIB] - $it"
        }
    }
}

target(listAppDependencies: "Display a list of Ivy dependencies for this Grails project") {
    println "Retrieving app dependencies"
    getAppDependencies().each {
        println "- $it"
    }
}

target(listSkinnyAppDependencies: "Display a list of dependencies for the skinny war") {
    println "Retrieving skinny app dependencies"
    getSkinnyAppDependencies().each {
        println "- $it"
    }
}

