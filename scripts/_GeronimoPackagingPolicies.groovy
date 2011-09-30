// Imports

import groovy.xml.MarkupBuilder

// Includes

includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoUserArgs.groovy")
includeTargets << new File(geronimoPluginDir, "scripts/_GeronimoModules.groovy")

// Maven XML utilities

// Returns artifact root path (used as maven build dir)
def getArtifactRootPath = { geronimoModule ->
	"${getConfigUtil().getMavenBaseDir()}/${geronimoModule.artifactId}"
}

createMavenXmlFile = { geronimoModule, builderFunc, relativeDirPathNoTrailingSlash, xmlFileName ->
	def dirPathNoTrailingSlash = "${getArtifactRootPath(geronimoModule)}/${relativeDirPathNoTrailingSlash}"
	new File( "${dirPathNoTrailingSlash}/" ).mkdirs()
	def xmlWriter = new FileWriter( "${dirPathNoTrailingSlash}/${xmlFileName}" )
	builderFunc( new MarkupBuilder(xmlWriter), geronimoModule )
}

// Routes pom generation to parameter policy
createPomXmlFile = { geronimoModule, builderFunc ->
	createMavenXmlFile( geronimoModule, builderFunc, "", "pom.xml" )
}

// Will generate a maven plan xml file
createPlanXmlFile = { geronimoModule, builderFunc ->
	createMavenXmlFile( geronimoModule, builderFunc, "src/main/plan", "plan.xml" )
}

// Will generate a ra xml file
createRaXmlFile = { geronimoModule, builderFunc ->
	createMavenXmlFile( geronimoModule, builderFunc, "src/main/rar/META-INF", "ra.xml" )
}

// Will generate a geronimo-ra xml file
createGeronimoRaXmlFile = { geronimoModule, builderFunc ->
	createMavenXmlFile( geronimoModule, builderFunc, "src/main/rar/META-INF", "geronimo-ra.xml" )
}

// Generates xml necessary for maven packaging (such as pom and plan xmls)
conditionalCreateMavenXmlFiles = { geronimoModule, policy ->
	return geronimoModule.shouldGenerateMavenXml() ? policy.createMavenXmlFiles( geronimoModule ) : false
}

// Car policy

geronimoCarPolicy = [
	
	// Populates a pom xml file for building a car
	// Arguments: 
	//      xml - the markup builder used for generating the xml
	//      geronimoModule: - a module containing metadata such as artifact id, group id, and dependencies
	buildPomXml : { xml, geronimoModule ->
 		xml.project(xmlns: 'http://maven.apache.org/POM/4.0.0', 'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance', 'xsi:schemaLocation': 'http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd') {
	        modelVersion('4.0.0')
	        parent() {
	            groupId('org.apache.geronimo.genesis.config')
	            artifactId('project-config')
	            version('1.5')
	        }
	        groupId(geronimoModule.groupId)
	        artifactId(geronimoModule.artifactId)
	        name(geronimoModule.mavenName)
	        packaging(geronimoModule.packaging)
	        version(geronimoModule.version)
	        properties() {
	            geronimoVersion(getConfigUtil().getGeronimoVersion())
	            projectName(geronimoModule.getMavenName())
	        }
	        dependencies {
	            dependency {
	                groupId('org.apache.geronimo.framework')
	                artifactId('geronimo-gbean-deployer')
	                version('${geronimoVersion}')
	                type('car')
	                scope('provided')
	            }
	            dependency {
	                groupId('org.apache.geronimo.configs')
	                artifactId('j2ee-deployer')
	                version('${geronimoVersion}')
	                type('car')
	                scope('provided')
	            }
	            dependency() {
	                groupId('org.apache.geronimo.framework')
	                artifactId('jee-specs')
	                version('${geronimoVersion}')
	                type('car')
	                scope('provided')
	            }
	            geronimoModule.dependencies.each { dep ->
	                dependency() {
	                    groupId( dep.getMavenGroupAndArtifactIds( getIvyToMavenArtifactMap() ).groupId )
	                    artifactId( dep.getMavenGroupAndArtifactIds( getIvyToMavenArtifactMap() ).artifactId )
	                    version( dep.version )
	                    type( dep.packaging )
	                }
	            }
	        }
	        build() {
	            plugins() {
	                plugin() {
	                    groupId('org.apache.geronimo.buildsupport')
	                    artifactId('car-maven-plugin')
	                    version('${geronimoVersion}')
	                    extensions('true')
	                    configuration() {
	                        archive() {
	                            addMavenDescriptor('false')
	                        }
	                        category('Geronimo Plugins')
	                        osiApproved('true')
	                        useMavenDependencies() {
	                            value('true')
	                            includeVersion('true')
	                        }
	                        commonInstance {
	                            "plugin-artifact" {
	                                "source-repository"("~/.m2/repository/")
	                                "source-repository"("http://repo1.maven.org/maven2/")
	                            }
	                        }
	                    }
	                }
	            }
	        }
	    }
	},
	
	// Generates a plan xml for car packaging
	buildPlanXml : { xml, geronimoModule ->
	    xml.module(xmlns:'http://geronimo.apache.org/xml/ns/deployment-1.2') {
	        environment {
	            'hidden-classes' {
	                filter('org.jaxen')
	                filter('org.springframework')
	                filter('org.apache.cxf')
	                filter('org.apache.commons')
	            }
	        }
	    }
	},
	
	createMavenXmlFiles : { geronimoModule ->
		createPomXmlFile( geronimoModule, geronimoCarPolicy.buildPomXml )
		createPlanXmlFile( geronimoModule, geronimoCarPolicy.buildPlanXml )
		return true
	},
	
	// Returns list of server resources to load into classpath
	getExternalDependencies : { getAppDependencies() + getLibDependencies() - getSkinnyAppDependencies() },
	
	// Local library jars should always be deployed when requested
	shouldDeployLibs : { true },

	// Does the actual work of deploying each individual car (does not build the cars)
	doDeployCommonResourcePackages : {
	   new File(getConfigUtil().getMavenBaseDir()).eachDir { File pluginBaseDir ->
	        def pluginCarDir = new File( "${pluginBaseDir}/target" )        
	        pluginCarDir.eachFileMatch(~/.*\.car/) {
	            execGshCmd( getConfigUtil().getGeronimoHome(), "deploy/install-plugin ${it.absolutePath} -u ${getConfigUtil().getGeronimoUser()} -w ${getConfigUtil().getGeronimoPass()}" )
	        }
	    }
	}
]

// Rar policy

geronimoRarPolicy = [

	// Populates a pom xml file for building a Rar (Resource adapter)
	// This pom files performs the following on a 'mvn package' command:
	// 1.) Pushes remote/repo dependencies to the maven build directory using the maven-dependency-plugin
	// 2.) Pushes local jar dependencies to the maven build directory using the maven-antrun-plugin
	// 3.) Bundles all the jars collected form 1.) and 2.) into a resource adapter (rar) using the maven-rar-plugin 
	// The rar file is intended to be deployed to geronimo allowing dependency deployment to be a push model
	// Arguments:
	//      xml:(markupBuilder), - the markup builder used for generating the xml
	//      geronimoModule: - a module containing metadata such as artifact id, group id, and dependencies
	buildPomXml : { xml, geronimoModule ->
	    xml.project(xmlns: 'http://maven.apache.org/POM/4.0.0', 'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance', 'xsi:schemaLocation': 'http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd') {
	        modelVersion('4.0.0')
			name(geronimoModule.mavenName)
	        groupId(geronimoModule.groupId)
	        artifactId(geronimoModule.artifactId)
			packaging(geronimoModule.packaging)
	        version(geronimoModule.version)
			parent() {
	            groupId('org.apache.geronimo.genesis.config')
	            artifactId('project-config')
	            version('1.5')
	        }
			build() {
	            plugins() {

					// maven-dependency-plugin: copy remote/repo dependencies to build directory
					// see http://maven.apache.org/plugins/maven-dependency-plugin/usage.html
					if ( geronimoModule.dependencies ) {
	                	plugin() {
		                    groupId('org.apache.maven.plugins')
		                    artifactId('maven-dependency-plugin')
		                    executions() {
								execution() {
									id("copy-jars-to-build-dir")
									phase("process-resources")
									goals() {
										goal("copy")
									}
									configuration() {
										artifactItems() {
											// Copy remote/repo dependencies to build dir
											geronimoModule.dependencies.each { dep ->
							                	artifactItem() {
							                    	groupId( dep.getMavenGroupAndArtifactIds( getIvyToMavenArtifactMap() ).groupId )
							                    	artifactId( dep.getMavenGroupAndArtifactIds( getIvyToMavenArtifactMap() ).artifactId )
							                    	version( dep.version )
							                    	type( dep.packaging )
							                	} // end artifact item
							            	} // end dependency iteration
										} // end artifact items
										outputDirectory('${project.build.directory}/${project.artifactId}-${project.version}')
									} // end configuration
								} // end execution
							} // end executions
		                } // end maven-dependency-plugin
					}

					// maven-ant-run-plugin: copy local jars to build directory
					if ( geronimoModule.libs ) {
						plugin() {
							artifactId("maven-antrun-plugin")
							executions() {
								execution() {
									phase("process-resources")
									configuration() {
										tasks() {
											geronimoModule.libs.each { lib ->
												copy( file: lib.file.getAbsolutePath(), toDir:'${project.build.directory}/${project.artifactId}-${project.version}')
											} // end iteration over local jar libraries
										} // end tasks
									} // end configuration
									goals() {
										goal( "run" )
									} // end goals
								} // end execution
							} // end executions
						} // end maven-ant-run-plugin
					}
						
					// maven-rar-plugin: bundle jars into a resource adapter (rar)
					// see http://maven.apache.org/plugins/maven-rar-plugin/rar-mojo.html
					plugin() {
						groupId("org.apache.maven.plugins")
						artifactId("maven-rar-plugin")
						configuration() {
							includeJar("false")
						} // end configuration
					} // end maven-rar-plugin

	            } // end plugins
	        } // end build
		} // end project
	}, // end generatePomXml
	
	buildRaXml : { xml, geronimoModule ->
		xml.mkp.xmlDeclaration(version:'1.0', encoding:"UTF-8")
	    xml.connector(xmlns:"http://java.sun.com/xml/ns/j2ee", "xmlns:xsi":"http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation":"http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/connector_1_5.xsd", version:"1.5") {
			"vendor-name"( geronimoModule.groupId )
			"eis-type"()
			"resourceadapter-version"( geronimoModule.version )
			resourceadapter()
		}
	},
	
	buildGeronimoRaXml : { xml, geronimoModule ->
		xml."conn:connector"("xmlns:dep": "http://geronimo.apache.org/xml/ns/deployment-1.2", "xmlns:conn": "http://geronimo.apache.org/xml/ns/j2ee/connector-1.2") {
			"dep:environment" {
				"dep:moduleId" {
					"dep:groupId"( geronimoModule.groupId )
					"dep:artifactId"( geronimoModule.artifactId )
					"dep:version"( geronimoModule.version )
					"dep:type"( geronimoModule.packaging )
				} // end of module id
			} // end of environment
			"conn:resourceadapter"()
		} // end of connector
	},
	
	createMavenXmlFiles : { geronimoModule ->
		createPomXmlFile( geronimoModule, geronimoRarPolicy.buildPomXml )
		createRaXmlFile( geronimoModule, geronimoRarPolicy.buildRaXml )
		createGeronimoRaXmlFile( geronimoModule, geronimoRarPolicy.buildGeronimoRaXml )
		return true
	},
	
	// Returns list of server resources to load into classpath
	getExternalDependencies : { getProvidedModules().findAll { it.shouldGenerateMavenXml() } },
	
	// Local library jars should never be deployed when requested as they are bundled in the rars
	shouldDeployLibs : { false },
	
	// Does the actual work of deploying each individual rar (does not build the rars)
	doDeployCommonResourcePackages : {
	   new File(getConfigUtil().getMavenBaseDir()).eachDir { File pluginBaseDir ->
	        def pluginRarDir = new File( "${pluginBaseDir}/target" )
	        pluginRarDir.eachFileMatch(~/.*\.rar/) {
	            execGshCmd( getConfigUtil().getGeronimoHome(), "deploy/deploy ${it.absolutePath} -u ${getConfigUtil().getGeronimoUser()} -w ${getConfigUtil().getGeronimoPass()}" )
	        }
	    }
	}
]

// Global packaging policy

// Returns true if packaging is configured for Rar packaging
def shouldUseRarPackagingPolicy = { getConfigUtil().getMavenPackaging() == "rar" }

// Select policy type based on packing configuration
def geronimoPackagingPolicyMap = shouldUseRarPackagingPolicy() ? geronimoRarPolicy : geronimoCarPolicy

// Return our packaging policy
getGeronimoPackagingPolicy = { geronimoPackagingPolicyMap }
