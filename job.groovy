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
    configure { promotion ->
        promotion / buildSteps << 'hudson.plugins.groovy.SystemGroovy plugin="groovy@2.0"' {
            'source class="hudson.plugins.groovy.StringSystemScriptSource"' {
                'script plugin="script-security@1.50"' {
                    sandbox(false)
                    script('println "hello, world"')
                }
            }
        }
    }

    parameters {
        stringParam('ADAMSBuildNumber', null, 'Displayed Build Number')
        stringParam('JIRA_KEY', 'none', 'JIRA Key for Build')
    }
}
