//
// This script is executed by Grails when the plugin is uninstalled from project.
// Use this script if you intend to do any additional clean-up on uninstall, but
// beware of messing up SVN directories!
//

// Remove configuration file
ant.delete(file:"${basedir}/grails-app/conf/grails-geronimo/_GeronimoConfig.groovy", failonerror:false)

