# Grails Geronimo Plugin

A [Grails](http://www.grails.org/) plugin allowing for creation of _skinny_ and _fat_ wars for deployment on Geronimo servers. Fat wars are the same as those produced from the `grails war` bundling with the sole addition of a `geronimo-web.xml` configuration. Skinny wars are stripped of all grails-core and plugin jar dependencies. These dependencies are instead bundled as Geronimo plugin car files. They can be deployed independently of the skinny war and reduce bloat by avoiding jar duplication among multiple grails applications.

## Authors

* Cedric Hurst (cedric@spantree.net)
* Alan Perez-Rathke (alan@spantree.net)

## Installation

Follow the plugin installation instructions from the [Grails Plugin User Guide](http://grails.org/doc/latest/guide/12.%20Plug-ins.html#12.1 Creating and Installing Plug-ins). Here is a brief set of installation instructions:

1. Obtain a copy of the plugin zip file. Currently, this can be accomplished by `git fork`-ing the plugin repository and then running `grails package-plugin`.
2. Navigate to your Grails application directory and type `grails install-plugin (path to Grails Geronimo Plugin zip)`

## Basic Configuration and Usage

The following sections are meant to be a quick-start guide for configuring and using the plugin. Only major options and commands will be covered. For more advanced usage, refer to `Advanced Configuration and Usage`.

### Basic Configuration

Fat and skinny wars may be generated with no additional configuration necessary. However, to allow Grails to deploy to a Geronimo server, additional configuration must be specified in your application's `grails-app/conf/grails-Geronimo/_GeronimoConfig.groovy`. In the `geronimoDefaultSettings` map, enter values for the `geronimo-home`, `geronimo-user`, and `geronimo-pass` fields. These values are used for specifying the path to your Geronimo installation, your administrative user name, and your administrative password respectively. Grails will use these values to deploy war files and their associated dependency car files.

### Basic Usage

After installing and configuring the plugin, simply type `grails deployWar`. That`s it! By default, this command will package the Grails application as a skinny war, package grails-core and plugin dependencies as Geronimo car plugins, and finally deploy all necessary war, car, and jar files to the Geronimo server.

If deploying the application isn't desired, simply type `grails skinnyWar`. By default, this command will package the Grails application as a skinny war and package grails-core and plugin dependencies as Geronimo car plugins.

## Advanced Configuration and Usage

The following sections detail all configuration options and commands. For a quick-start guide, refer to `Basic Configuration and Usage`.

### Advanced Configuration

All configuration options are specified in `grails-app/conf/grails-Geronimo/_GeronimoConfig.groovy`. 

#### geronimoDefaultSettings

The geronimoDefaultSettings map contains options for war generation and geronimo deployment.

* `geronimo-home` : Specifies the path to the Geronimo installation necessary. Necessary for running GShell during war deployment.
* `geronimo-user` : The Geronimo admin user name. Necessary for deploying war files.
* `geronimo-pass` : The Geronimo admin password. Necessary for deploying war files.
* `geronimo-staging-dir` : The temporary staging dir for deploying library jars. Library jars are jar files located in plugin `lib` directories.
* `no-geronimo-cars` : Must be `true` or `false`. If true, car files for grails-core and plugin dependencies will not be generated when generating a skinny war. This is useful for improving iteration times if they have already been generated. If false, dependency car files will always be generated whenever a skinny war is generated.
* `no-geronimo-war` : Must be `true` or `false`. If true, a skinny war will not be built during war deployment.  If false, a skinny war will be generated during war deployment.
* `no-geronimo-deploy-cars` : Must be `true` or `false`. If true, cars files for grails-core and plugin dependencies will not be deployed when deploying a war file.  This is useful for improving iteration times if they have already been deployed.  If false, the cars will be deployed. 
* `no-geronimo-deploy-libs` : Must be `true` or `false`.  If true, jar files residing in plugin `lib` directories will not be deployed as libraries during skinny war deployment.  This is useful for improving iteration times if they have already been deployed.  If false, the jar files will be deployed.
* `geronimo-version` : Currently, this is not meant to be modified by the end user.  This simply states the highest version of Geronimo that is supported by the Grails Geronimo Grails Plugin.

These options may also be specified via commandline.  For example, `grails -Dno-geronimo-deploy-cars deployWar` will deploy a skinny war without deploying grails-core and plugin dependencies.

#### ivyToMavenArtifactMap

Maven (with the Geronimo Maven Car Plugin) is used for packaging grails-core and plugin dependencies into deployable Geronimo plugin car files.  Since Grails uses Ivy, certain dependencies must be converted to Maven names in order to work with Maven packaging.  This map is meant to do just that.  It maps an Ivy `group ID:artifact ID` key string to a map containing the corresponding Maven group and artifact IDs.  This map is exposed in `_GeronimoConfig.groovy` in case additional artifacts need to be coverted.

#### mavenSettings

This mapping specifies additional maven configuration such as packaging and application version.  The parameter 'maven-opts' may be specified via commandline (e.g. - `grails -Dmaven-opts='-X' generateCars`).

### Advanced Usage

The following is a list of all commands available for the Geronimo Grails Plugin.  To run these commands, simply type `grails (command name)`.

#### Dependency Listing

* `list-core-dependencies` : Echos all grails-core dependencies
* `list-plugin-dependencies` : Echos all installed plugin dependencies for this grails application
* `list-app-dependencies` : Echos all application runtime dependencies (including core and plugin dependencies)
* `list-skinny-app-dependencies` : Echos all application runtime dependencies that are not core or plugin dependencies
* `list-lib-dependencies` : Echos all jar dependencies which reside in the `lib` folders of installed plugins

#### Fat/Skinny War Generation

* `stageCore` : Generates Maven pom.xml and plan.xml files for grails-core which can be packaged into Geronimo plugins
* `stagePlugins` : Generates a Maven pom.xml and plan.xml for each installed plugin
* `generateCars` : Stages and packages grails-core and installed Grails plugins as deployable Geronimo plugin car files
* `fatWar` : Similar to `grails war` but also bundles a `geronimo-web.xml` within the war to allow for Geronimo deployment.
* `skinnyWar` : Similar to `fatWar` but grails-core and installed Grails plugins are bundled as external Geronimo plugin car files.

#### Geronimo Deployment

* `deployWar` : By default will generate and deploy all necessary war, car, and jar files to the Geronimo server.
* `deployLibs` : Deploys jar files residing in the `lib` folder of installed Grails plugins to the Geronimo server.
* `deployCars` : Deploys grails-core and installed Grails plugins as Geronimo plugins.
