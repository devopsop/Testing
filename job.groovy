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
                    groovyScriptFile('''
import groovy.json.JsonSlurper
import hudson.EnvVars
import hudson.model.Environment

def buildn= build.buildVariableResolver.resolve("ADAMSBuildNumber")

def thr = Thread.currentThread();
def currentBuild = thr?.executable;
def workspace = currentBuild.getModuleRoot().absolutize().toString();
println workspace

def inputFile = new File(workspace + "/build" + buildn + ".txt")
def InputJSON = new JsonSlurper().parseText(inputFile.text)

def vardeploypath = (InputJSON =~ /deployPath:\\s(.+)/)[0][1]
def varDEPLOY_VERSION = (InputJSON =~ /DEPLOY_VERSION:\\s(.+)/)[0][1]
def varsqlVersion = (InputJSON =~ /sqlVersion:\\s(.+)/)[0][1]
def varmobileVersion = (InputJSON =~ /mobileVersion:\\s(.+)/)[0][1]
def varmobileDeployPath = (InputJSON =~ /mobileDeployPath:\\s(.+)/)[0][1]

def build = Thread.currentThread().executable

def vars = [deployPath: vardeploypath ,DEPLOY_VERSION: varDEPLOY_VERSION ,sqlVersion: varsqlVersion ,mobileVersion: varmobileVersion,mobileDeployPath: varmobileDeployPath]

build.environments.add(0, Environment.create(new EnvVars(vars)))
''')                 
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
