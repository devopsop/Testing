def curJob = job('TESTCENTER-API_BUILD') {
    description('Job to build testcenter-api')

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
                url('https://bitbucket.wada-ama.org/scm/adams-ng/testcenter-api.git')
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
            //npmrc settings
            file('700b68f6-df2b-4c6a-b8b0-697abc114d2b') {
                targetLocation('.npmrc')
            }
        }
    }
    steps {
        shell('''
TESTCENTER_API_VERSION=$(cat package.json \\
  | grep version \\
  | head -1 \\
  | awk -F: '{ print $2 }' \\
  | sed 's/[",]//g' \\
  | tr -d '[[:space:]]')
echo "TESTCENTER_API_VERSION=${TESTCENTER_API_VERSION}" > versionfile
npm config list
npm --no-git-tag-version version $TESTCENTER_API_VERSION-$RELEASE_BUILD_NUMBER
npm install
npm run bundle
npm publish
''')
        environmentVariables {
            propertiesFile('versionfile')
        }
        httpRequest('https://nexus.wada-ama.org/service/rest/beta/search?q=${TESTCENTER_API_VERSION}&repository=ADAMS-Snapshots&group=com.cgi&name=testcenter-api') {
            httpMode('GET')
            authentication('5f71b8c2-4ced-45aa-9da9-014c32323dec')
            returnCodeBuildRelevant()
            logResponseBody()
            outputFile('nexus.log')
        }
        shell('''
if cat nexus.log | grep '"items" : \\[ \\],'
then
	echo "TESTCENTER_API_VERSION=${TESTCENTER_API_VERSION}-RELEASE" > versionfile
    echo "NEXUS_CLASSIFIER=RELEASE" >> versionfile
else
	echo "TESTCENTER_API_VERSION=${TESTCENTER_API_VERSION}" > versionfile
    echo "NEXUS_CLASSIFIER=SNAPSHOT" >> versionfile
fi
''')
        environmentVariables {
            propertiesFile('versionfile')
        }
        nexusArtifactUploader {
            nexusVersion('nexus3')
            protocol('https')
            nexusUrl('nexus.wada-ama.org')
            groupId('com.cgi')
            version('${TESTCENTER_API_VERSION}-SNAPSHOT')
            repository('ADAMS-Snapshots')
            credentialsId('003f5c19-50c1-4ae3-a296-f23e630c2bb4')
            artifact {
                artifactId('testcenter-api')
                type('zip')
                classifier('${NEXUS_CLASSIFIER}')
                file('testcenter-api.zip')
            }
        }
    }

}
