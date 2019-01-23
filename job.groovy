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
                    systemGroovyCommand('import groovy.json.JsonSlurper\nimport hudson.EnvVars\nimport hudson.model.Environment\n\ndef buildn= build.buildVariableResolver.resolve("ADAMSBuildNumber")\n\ndef thr = Thread.currentThread();\ndef currentBuild = thr?.executable;\ndef workspace = currentBuild.getModuleRoot().absolutize().toString();\nprintln workspace\n\ndef inputFile = new File(workspace + "/build" + buildn + ".txt")\ndef InputJSON = new JsonSlurper().parseText(inputFile.text)\n\ndef vardeploypath = (InputJSON =~ /deployPath:\\s(.+)/)[0][1]\ndef varDEPLOY_VERSION = (InputJSON =~ /DEPLOY_VERSION:\\s(.+)/)[0][1]\ndef varsqlVersion = (InputJSON =~ /sqlVersion:\\s(.+)/)[0][1]\ndef varmobileVersion = (InputJSON =~ /mobileVersion:\\s(.+)/)[0][1]\ndef varmobileDeployPath = (InputJSON =~ /mobileDeployPath:\\s(.+)/)[0][1]\n\ndef build = Thread.currentThread().executable\n\ndef vars = [deployPath: vardeploypath ,DEPLOY_VERSION: varDEPLOY_VERSION ,sqlVersion: varsqlVersion ,mobileVersion: varmobileVersion,mobileDeployPath: varmobileDeployPath]\n\nbuild.environments.add(0, Environment.create(new EnvVars(vars)))') {
                        sandbox(false)
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
