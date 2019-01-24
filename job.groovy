def curJob = job('ADAMS-PROMOTIONS-DEV-DEVOPS') {
    properties {
        promotions {
            promotion {
                name('ADAMS Promotion DEV')
                icon('Green star')
                conditions {
                    manual('ACL_CDCI_deploy_CH,ACL_CDCI_deploy_QA,ACL_CDCI_deploy_ADMIN')
                }
                actions {
                    httpRequest {
                        url('https://wada-ama.atlassian.net/rest/api/2/search?jql=id=${JIRA_KEY}&fields=description')
                        httpMode('GET')
                        acceptType('APPLICATION_JSON')
                        contentType('APPLICATION_JSON')
                        authentication('bitbucket_public_key')
                        outputFile('build$ADAMSBuildNumber.txt')                        
                    }    
                    shell('''
deployPath=$(grep -Po 'deployPath:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)
DEPLOY_VERSION=$(grep -Po 'DEPLOY_VERSION:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)
sqlVersion=$(grep -Po 'sqlVersion:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)
mobileVersion=$(grep -Po 'mobileVersion:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)
mobileDeployPath=$(grep -Po 'mobileDeployPath:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)

echo "deployPath=${deployPath}" > varfile
echo "DEPLOY_VERSION=${DEPLOY_VERSION}" >> varfile
echo "sqlVersion=${sqlVersion}" >> varfile
echo "mobileVersion=${mobileVersion}" >> varfile
echo "mobileDeployPath=${mobileDeployPath}" >> varfile
                    ''')
                    environmentVariables {
                        propertiesFile('varfile')
                    }
                    downstreamParameterized {
                        trigger('ADAMS_DEPLOY_CGI_MERGED') {
                            parameters {
                                predefinedProp('DEPLOY_VERSION', '$DEPLOY_VERSION')
                                predefinedProp('deployPath', '$deployPath')
                                predefinedProp('sqlVersion', '$sqlVersion')
                                predefinedProp('mobileVersion', '$mobileVersion')
                                predefinedProp('mobileDeployPath', '$mobileDeployPath')
                                predefinedProp('JIRA_KEY', '${JIRA_KEY}')
                            }
                        }
                    }
                }
            }
        }
    }

    parameters {
        stringParam('ADAMSBuildNumber', null, 'Displayed Build Number')
        stringParam('JIRA_KEY', 'none', 'JIRA Key for Build')
    }
}
