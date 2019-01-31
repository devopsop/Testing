def curJob = job('ATHLETECENTRAL_API_BUILD_MERGED') {
    description('Job to build AthleteCentral API')

    // We only keep the last 30 builds
    logRotator {
        numToKeep(30)
    }

    parameters {
        stringParam('branch', 'develop', 'The branch from which build the application')
    }

    scm {
        git {
            branch('refs/heads/${branch}')
            remote {
                url('https://bitbucket.wada-ama.org/scm/adams-ng/athletehub-api.git')
                //Credentials for the build_agent user
                credentials('003f5c19-50c1-4ae3-a296-f23e630c2bb4')
            }
            extensions {
                submoduleOptions {
                    recursive()
                    tracking()
                    parentCredentials()
                }
            }
        }
    }

    //this section sets the build number formatter the same way it's made for adams
    configure { project ->
        project / buildWrappers << 'org.jvnet.hudson.tools.versionnumber.VersionNumberBuilder' {
            versionNumberString '${BUILD_DATE_FORMATTED,"yyDDD"}${BUILDS_TODAY,XX}'
            projectStartDate '1969-12-31 05:00:00.0 UTC'
            environmentVariableName 'RELEASE_BUILD_NUMBER'
            environmentPrefixVariable
            oBuildsToday
            oBuildsThisWeek
            oBuildsThisMonth
            oBuildsThisYear
            oBuildsAllTime
            worstResultForIncrement 'NOT_BUILT'
            skipFailedBuilds
            useAsBuildDisplayName
        }

    }

    wrappers {
        configFiles {
            //JVM config
            file('fa4c4c5b-29b3-46f1-8cd8-5b83b07f491c') {
                targetLocation('.mvn/jvm.config')
            }
        }

        environmentVariables {
            groovy('''import jenkins.util.*;
import jenkins.model.*;

def thr = Thread.currentThread();
def currentBuild = thr?.executable;
def workspace = currentBuild.getModuleRoot().absolutize().toString();

def project = new XmlSlurper().parse(new File("$workspace/pom.xml"))

return [
"POM_VERSION": project.version.toString(), 
"POM_GROUPID": project.groupId.toString(),
"POM_ARTIFACTID": project.artifactId.toString(),
]''')
        }

    }

    steps {
        maven {
            goals('clean')
            goals('deploy')
            mavenInstallation('maven 3.3.9')
            injectBuildVariables(false)
            localRepository(LocalRepositoryLocation.LOCAL_TO_WORKSPACE)
            property('wada.scm.commitId', '${GIT_COMMIT}')
            property('wada.scm.branch', '${GIT_BRANCH}')
            property('wada.scm.tag', 'builds/${POM_VERSION}-${RELEASE_BUILD_NUMBER}')
            property('wada.build.number', '${RELEASE_BUILD_NUMBER}')
            property('wada.build.timestamp', '${BUILD_TIMESTAMP}')
            property('wada.build.user', '${BUILD_USER}')
            property('build.jobName', '${JOB_NAME}')
            property('maven.test.skip','true')
            //Same settings as adams
            providedSettings('59d605db-7b8a-47c3-b220-6af4dc4facf0')
        }
    }
    publishers {
        groovyPostBuild('''
import hudson.EnvVars
import hudson.model.Environment

fileContents = manager.build.logFile.text
def result= (fileContents =~ /Uploaded to nexus:\\s.+\\.jar/)
def sonarRes = (fileContents =~ /\\[INFO\\] ANALYSIS SUCCESSFUL,\\s.*/)

if (result.getCount() != 0) {
  vers = (result[0] =~ /http.*\\.jar/)[0].split('/')[9] + '/' + (result[0] =~ /http.*\\.jar/)[0].split('/')[10]
} else {
  vers = "Error"
}

if (sonarRes.getCount() != 0) {
  sonar = (sonarRes[0] =~ /http.*/)[0]
} else {
  sonar = "No sonar scan"
}

manager.listener.logger.println('SonarQube URL: ' + sonar)
manager.listener.logger.println('vers: ' + vers)

def build = Thread.currentThread().executable
def vars = [DEPLOY_VERSION: "Reserved for future use once the build jobs push to nexus",SONAR_URL: sonar]
build.environments.add(0, Environment.create(new EnvVars(vars)))
''', Behavior.DoNothing)
        git {
            pushOnlyIfSuccess()
            tag('origin','builds/${POM_VERSION}-${RELEASE_BUILD_NUMBER}') {
                create()
                message('Automated build.\n' +
                        'Built by ${BUILD_USER}')
            }
        }
        downstreamParameterized {
            trigger('CREATE_JIRA_TICKET_ATHLETECENTRAL') {
                condition('SUCCESS')
                parameters {
                    predefinedProp('DEPLOY_VERSION', '$DEPLOY_VERSION')
                    predefinedProp('ADAMSBuildNumber', '${RELEASE_BUILD_NUMBER}')
                    predefinedProp('SONAR_URL', '$SONAR_URL')
                    predefinedProp('CHEF_COMMAND', 'deployAthleteCentral')
                }
            }
        }
    }
}
