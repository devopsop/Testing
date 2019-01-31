def curJob = mavenJob('ADRQ_BUILD_MERGED') {
    description('Job to build ADRQ')

    // We only keep the last 30 builds
    logRotator {
        numToKeep(30)
    }

    parameters {
        stringParam('branch', 'release/3.1.0', 'Branch name to build and deploy')
    }

    scm {
        git {
            branch('refs/heads/${branch}')
            remote {
                url('https://build_agent@bitbucket.wada-ama.org/scm/adrq/adrq.git')
                //Credentials for the build_agent user
                credentials('003f5c19-50c1-4ae3-a296-f23e630c2bb4')
            }
        }
    }

    //this section sets the build number formatter the same way it's made for adams
    configure { project ->
        project / buildWrappers << 'org.jvnet.hudson.tools.versionnumber.VersionNumberBuilder' {
            versionNumberString '${BUILD_DATE_FORMATTED,"yyDDD"}${BUILDS_ALL_TIME,XXXX}'
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
            useAsBuildDisplayName true
        }
    }

    wrappers {
        withSonarQubeEnv {
            installationName('sonarqube')
        }
        buildUserVars()
    }

    preBuildSteps {
        shell('java -version')
    }

    rootPOM('pom.xml')
    goals('-Dwada.scm.commitId="${GIT_COMMIT}" -Dwada.scm.branch="${GIT_BRANCH}" -Dwada.build.number="${RELEASE_BUILD_NUMBER}" -Dwada.build.timestamp="${BUILD_TIMESTAMP}" -Dwada.build.user="${BUILD_USER}" -Dbuild.jobName="${JOB_NAME}" clean deploy')

    publishers {
        groovyPostBuild('''
import hudson.EnvVars
import hudson.model.Environment

fileContents = manager.build.logFile.text
def result= (fileContents =~ /Uploaded to nexus:\\s.+\.war/)
def sonarRes = (fileContents =~ /\\[INFO\\] ANALYSIS SUCCESSFUL,\\s.*/)

if (result.getCount() != 0) {
  vers = (result[0] =~ /http.*\\.war/)[0].split('/')[9] + '/' + (result[0] =~ /http.*\\.war/)[0].split('/')[10]
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
def vars = [DEPLOY_VERSION: vers,SONAR_URL: sonar]
build.environments.add(0, Environment.create(new EnvVars(vars)))
''', Behavior.DoNothing)
        git {
            pushOnlyIfSuccess()
            tag('origin','builds/${POM_VERSION}-${RELEASE_BUILD_NUMBER}') {
                create()
            }
        }

        downstreamParameterized {
            trigger('CREATE_JIRA_TICKET_ADRQ') {
                condition('SUCCESS')
                parameters {
                    predefinedProp('DEPLOY_VERSION', '$DEPLOY_VERSION')
                    predefinedProp('ADAMSBuildNumber', '${RELEASE_BUILD_NUMBER}')
                    predefinedProp('SONAR_URL', '$SONAR_URL')
                }
            }
        }
    }
}
