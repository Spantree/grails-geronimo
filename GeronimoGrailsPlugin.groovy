class GeronimoGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "Cedric Hurst, Alan Perez-Rathke"
    def authorEmail = "cedric@spantree.net, alan@spantree.net"
    def title = "This plugin is designed to generate Geronimo deployment plans and 'skinny' WARs from a given Grails project"
    def description = '''
Geronimo-Grails plugin allows creation of skinny or fat wars for deployment on Geronimo.  Fat wars are the same as those produced from the grails war bundling with addition of a geronimo-web.xml configuration.  Skinny wars are stripped of all grails-core and plugin dependencies (jars).  Instead, the core and plugin dependencies are bundled as geronimo plugin car files.  They can be deployed independently of the skinny war and reduce bloat by avoiding jar duplication.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/geronimo"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}
