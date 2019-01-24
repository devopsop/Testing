def curJob = job('ADAMS_BUILD_CGI_MERGED') {
    description('Job to build ADAMS for CGI integration pipeline')

    // We only keep the last 30 builds
    logRotator {
        numToKeep(30)
    }
    authenticationToken('TtOrZzFlqIbRrzfI1YahYcUi1mxTvkwx')

    parameters {
        stringParam('branch', null, 'The branch from which build the application')
    }

    scm {
        git {
            branch('refs/heads/${branch}')
            remote {
                url('https://bitbucket.wada-ama.org/scm/adams/adams-cgi.git')
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
            useAsBuildDisplayName(true)
        }

    }

    wrappers {
        configFiles {
            //Adams maven Settings
            file('a441713c-efba-4256-b01c-2c802a45c423') {
                targetLocation('.mvn/maven.config')
            }
            //JVM config
            file('fa4c4c5b-29b3-46f1-8cd8-5b83b07f491c') {
                targetLocation('.mvn/jvm.config')
            }
        }
        credentialsBinding {
            file('sign.keystore', 'af841b34-805b-440f-95d5-db599dafbb5d')
            usernamePassword('na', 'sign.storepass','326fc163-7b3a-48a3-b251-eb52466d9c80')
            usernamePassword('sign.alias', 'sign.keypass','cf02ce35-7ce5-4f37-aed2-a37073b69732')
        }
        environmentVariables {
            groovy('''import jenkins.util.*;
import jenkins.model.*;

def thr = Thread.currentThread();
def currentBuild = thr?.executable;
def workspace = currentBuild.getModuleRoot().absolutize().toString();

def project = new XmlSlurper().parse(new File("$workspace/adams_superpom/pom.xml"))

return [
"POM_VERSION": project.version.toString(), 
"POM_GROUPID": project.groupId.toString(),
"POM_ARTIFACTID": project.artifactId.toString(),
]''')
        }
        withSonarQubeEnv {
            installationName('sonarqube')
        }
        buildUserVars()
    }

    steps {
        maven {
            goals('clean')
            goals('deploy')
            goals('sonar:sonar -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.branch=$branch')
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
            property('sign.keystore','${sign.keystore}')
            property('sign.storepass','${sign.storepass}')
            property('sign.alias','${sign.alias}')
            property('sign.keypass','${sign.keypass}')
            //Same settings a adams
            providedSettings('59d605db-7b8a-47c3-b220-6af4dc4facf0')
        }
    }
    publishers {
        groovyPostBuild('''import hudson.EnvVars
import hudson.model.Environment

fileContents = manager.build.logFile.text
def result= (fileContents =~ /Uploaded to nexus:\\s.+\\.ear/)
def sonarRes = (fileContents =~ /\\[INFO\\] ANALYSIS SUCCESSFUL,\\s.*/)

if (result.getCount() != 0) {
  dep = (result[0] =~ /http.*\\.ear/)[0].split('/')[10]
  vers = (result[0] =~ /http.*\\.ear/)[0].split('/')[9]
} else {
  dep = "Error"
  vers = "Error"
}

if (sonarRes.getCount() != 0) {
  sonar = (sonarRes[0] =~ /http.*/)[0]
} else {
  sonar = "No sonar scan"
}

manager.listener.logger.println('SonarQube URL: ' + sonar)
manager.listener.logger.println('dep: ' + dep)
manager.listener.logger.println('vers: ' + vers)

def build = Thread.currentThread().executable
def vars = [deployPath: dep,DEPLOY_VERSION: vers,sqlVersion: "none",mobileVersion: "3.0.3.FINAL",SONAR_URL: sonar]
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

        configure { project ->
            project / publishers << 'com.veracode.jenkins.plugin.VeracodeNotifier' {
                __appname {AQAAABAAAAAQT6CZKqhS14tw7OQ54Gchrb8yngW8GRfTWp5H5QWTxa4=}
                __createprofile false
                __teams {AQAAABAAAAAQQ6T3cGmnRYN9WdVlFQ++IU4KbwGVFk9gMQfCDn3rr0Y=}
                __criticality {AQAAABAAAAAQ0OaL7f4s+Q+1wrzb/6rqaQVoKusZX19/q5VfnH78KtM=}
                __sandboxname {AQAAABAAAAAQIJOQAk32U5YmcnEigLig20gwsN68TLBXEkv6Sm6260U=}
                __createsandbox false
                __version {AQAAABAAAAAw+33Du1SLpsvVkFojd7L8ZTnY8bufOn29AUvqoGu77fS2dcJ2ERYOv3uB+stuBmDMcimqd5OSNUsRVN4Wrmju3w==}
                __uploadincludespattern {AQAAABAAAAAgdzBLH6jdNESZJnUU0+GR6ZQVhZDHbjPkQbzUPLUMBuD2TUUti3OEtVoSAhTFD1dd}
                __uploadexcludespattern {AQAAABAAAAAQIe9LLjJzVQvc5wZZxmhi2tZtjjLTvtGVPh7AFoJQ9k0=}
                __scanincludespattern {AQAAABAAAAAQvKdZ9wsnaTatTRnwasCNwhe0w8bUdLKycxBHIBMWhRw=}
                __scanexcludespattern {AQAAABAAAAAQmGgleIptpYg+OEUTPL2yb7nHfEJ7sej3jbwvG66Rg4U=}
                __filenamepattern {AQAAABAAAAAQKCvW5cU0GZGAzJ7aox5k81QmMrDkxnNNbKs/eLnt1qU=}
                __replacementpattern {AQAAABAAAAAQACPFBUq2nrq3CnMthjeTKIxpZyGpACtLiiEWxcWfX8U=}
                __waitforscan false
            }
        }

        downstreamParameterized {
            trigger('Create-JIRA-ticket-ADAMS') {
                condition('SUCCESS')
                parameters {
                    predefinedProp('DEPLOY_VERSION', '$DEPLOY_VERSION')
                    predefinedProp('deployPath', '$deployPath')
                    predefinedProp('sqlVersion', '$sqlVersion')
                    predefinedProp('mobileVersion', '$mobileVersion')
                    predefinedProp('mobileDeployPath', '$mobileDeployPath')
                    predefinedProp('JIRA_TICKETS', 'none')
                    predefinedProp('PARENT_WORKSPACE', 'none')
                    predefinedProp('ADAMSBuildNumber', '${RELEASE_BUILD_NUMBER}')
                    predefinedProp('SONAR_URL', '$SONAR_URL')
                }
            }
        }
    }
}
