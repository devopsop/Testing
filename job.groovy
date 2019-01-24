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
                    shell('''
curl -XGET "https://wada-ama.atlassian.net/rest/api/2/search?jql=id=${JIRA_KEY}&fields=description" -u ${jirauser}:${jirapw} > jira.json

echo "deployPath=$(grep -Po 'deployPath:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)" > varfile
echo "DEPLOY_VERSION=$(grep -Po 'DEPLOY_VERSION:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)" >> varfile
echo "sqlVersion=$(grep -Po 'sqlVersion:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)" >> varfile
echo "mobileVersion=$(grep -Po 'mobileVersion:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)" >> varfile
echo "mobileDeployPath=$(grep -Po 'mobileDeployPath:\\s.*?\\\\n' jira.json | cut -d' ' -f2 | cut -d'\\' -f1)" >> varfile
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

    wrappers {
        credentialsBinding {
            usernamePassword('jirauser', 'jirapw','bitbucket_public_key')
        }
    }
    
    parameters {
        stringParam('ADAMSBuildNumber', null, 'Displayed Build Number')
        stringParam('JIRA_KEY', 'none', 'JIRA Key for Build')
    }

    steps {
        shell('echo "Ready to deploy in CGI"')
    }
}
