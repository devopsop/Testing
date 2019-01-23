def curJob = job('Create-JIRA-ticket-ADAMS') {
    description('Called by ADAMS Build jobs to create a JIRA Ticket')

    // We only keep the last 30 builds
    logRotator {
        numToKeep(30)
    }

    parameters {
        stringParam('DEPLOY_VERSION', null, 'Version of the deployment')
        stringParam('deployPath', null, 'Name of the .ear')
        stringParam('sqlVersion', 'none', 'SQL Version')
        stringParam('mobileVersion', '3.0.3.FINAL', 'Mobile Version')
        stringParam('mobileDeployPath', 'mobile-server-3.0.3-FINAL.jar', 'Mobile Server file name')
        stringParam('JIRA_TICKETS', 'none', 'JIRA Ticket')
        stringParam('PARENT_WORKSPACE', null, 'Workspace')
        stringParam('ADAMSBuildNumber', null, 'Build Number. Release')
        stringParam('SONAR_URL', 'none', 'Sonarqube Scan URL')
        stringParam('PARENT_NAME', 'none', 'Parent Name')
    }

    steps {
        httpRequest {
            url('https://wada-ama.atlassian.net/rest/api/2/issue/')
            httpMode('POST')
            acceptType('APPLICATION_JSON')
            contentType('APPLICATION_JSON')
            authentication('bitbucket_public_key')
            outputFile('response.json')
            requestBody('''{
    "fields": {
       "project":
       {
          "key": "WDEPLOY"
       },
       "summary": "ADAMS Build Number: ${ADAMSBuildNumber}",
       "description": "h2. ADAMS Build: ${ADAMSBuildNumber}\\n\\n* deployPath: ${deployPath}\\n* DEPLOY_VERSION: ${DEPLOY_VERSION}\\n* sqlVersion: ${sqlVersion}\\n* mobileVersion: ${mobileVersion}\\n* mobileDeployPath: ${mobileDeployPath}\\n* JIRA_TICKETS: ${JIRA_TICKETS}\\n* PARENT_WORKSPACE: ${PARENT_WORKSPACE}\\n* SonarQube URL: ${SONAR_URL}",
       "issuetype": {
          "name": "Task"
       },
       "components": [{"name":"ADAMS"}]
   }
}
''')
        }
        shell('''JIRA_KEY=$(cat response.json | grep key \\
  | head -1 \\
  | awk -F, '{ print $2 }' \\
  | awk -F: '{ print $2 }' \\
  | sed 's/[",]//g' \\
  | tr -d '[[:space:]]')
echo "JIRA_KEY=${JIRA_KEY}" > jirakey
echo "JIRA ISSUE URL: https://wada-ama.atlassian.net/browse/${JIRA_KEY}" ''')
        environmentVariables {
            propertiesFile('jirakey')
        }
    }
    publishers {
        downstreamParameterized {
            trigger('ADAMS-PROMOTIONS-TEST-DEVOPS,ADAMS-PROMOTIONS-FUNCTIONAL-DEVOPS,ADAMS-PROMOTIONS-PREPROD-DEVOPS,ADAMS-PROMOTIONS-PROD-DEVOPS,ADAMS-PROMOTIONS-DEV-DEVOPS,ADAMS-PROMOTIONS-FUNCTIONAL2-DEVOPS,ADAMS-PROMOTIONS-TEST-NG-DEVOPS') {
                condition('SUCCESS')
                parameters {
                    predefinedProp('ADAMSBuildNumber', '${ADAMSBuildNumber}')
                    predefinedProp('JIRA_KEY', '${JIRA_KEY}')
                }
            }
        }
    }

}
