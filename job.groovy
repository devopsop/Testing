def curJob = job('ADAMS_DEPLOY_CGI_MERGED') {

    parameters {
        extensibleChoiceParameterDefinition {
            name('DEPLOY_VERSION')
            description('Adams version to be deployed')
            editable(true)
            choiceListProvider {
                systemGroovyChoiceListProvider {
                    defaultChoice("")
                    usePredefinedVariables(false)
                    groovyScript {
                        sandbox(false)
                        script("""
import java.util.regex.*;
import java.io.*;
import java.net.URLEncoder;

def getUserPassword = { username ->
    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
            com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
            jenkins.model.Jenkins.instance
    )

    def c = creds.findResult { it.username == username ? it : null }
    if ( c ) {
        return c.password.toString()
    } else {
        println "could not find credential for \$username"
    }
}

def username = "wadacdci"
def pass = java.net.URLEncoder.encode(getUserPassword(username), "UTF-8")
def sout = new StringBuilder(), serr = new StringBuilder()
def proc = ['/bin/bash', '-c', "git ls-remote --tags https://\\$username:\\$pass@bitbucket.wada-ama.org/scm/adams/adams.git builds/* | awk '\\\\\\$2 !~ /\\\\\\\\^\\\\\\\\{\\\\\\\\}/ && sub(\\\\\\"refs/tags/builds/\\\\",\\\\"\\\\",\\\\\\$2) {print \\\\\\$2}'"].execute()
proc.consumeProcessOutput(sout, serr)
proc.waitForOrKill(1000)
println("\\$serr");
return "\\$sout".tokenize().reverse()
""")
                    }
                }
            }
        }
        stringParam('deployPath', 'adams.ear', 'path of the adams.ear')
        stringParam('mobileVersion', '3.0.3.FINAL', 'Mobile Version to deploy')
        stringParam('mobileDeployPath', 'mobile-server-3.0.3-FINAL.jar', 'Mobile Version to deploy')
        stringParam('JIRA_TICKETS', 'none', 'space separated list of Jira tickets related to this build')
        stringParam('smssuppressValue', 'false', 'sms supression ')
        stringParam('smtpsuppressValue', 'false', 'smtp supression')
        stringParam('JIRA_KEY', 'none', 'JIRA Issue created for the build')

    }

    steps {
        shell('''
echo deploying in CGI

echo JIRA_ISSUES=$JIRA_TICKETS > env.properties
echo ADAMS_VERSION=$DEPLOY_VERSION >> env.properties
#echo mobileDeployPath=mobile-server-$mobileVersion.jar >> env.properties
sed  -e "s/\\(JIRA_ISSUES=\\)/\\1issueKey=/" -e "s/ \\(ADAMS-[0-9]\\{4\}\\)/ or issuekey=\\1/g" -i env.properties
sed -e "s/\\(^ADAMS_VERSION=[0-9.]*-[A-Z]*\\)-[0-9]*/\\1/g" -i env.properties
        ''')
        environmentVariables {
            propertiesFile('$WORKSPACE/env.properties')
        }
        shell('echo $ADAMS_VERSION')
        shell('''
knife environment show adams_cgi  -c /opt/cdci/chef/knife-test-new.rb  -F json | jq .cookbook_versions
sh /opt/databags/create-databag-test-cgi.sh "/org/wada/adams/adams-ear/$ADAMS_VERSION/$deployPath" "$deployPath" "/opt/databags/dev-data-bag.json" "$sqlVersion" "$mobileVersion" "$mobileDeployPath"
knife data bag from file cdci  /opt/databags/dev-data-bag.json --secret-file /opt/cdci/chef/secret_key -c /opt/cdci/chef/knife-test-new.rb
sh /opt/databags/create-databag-qaconfigs-test.sh "$smssuppressValue" "$smtpsuppressValue" "/opt/databags/dev-data-bag.json"
knife data bag from file jboss7_cgi  /opt/databags/dev-data-bag.json --secret-file /opt/cdci/chef/secret_key -c /opt/cdci/chef/knife-test-new.rb
knife job start chef-client WVTEMADA09.test.wada-ama.org -c /opt/cdci/chef/knife-test-new.rb --capture
        ''')
        progressJiraIssues {
            jqlSearch('id=${JIRA_KEY}')
            workflowActionName('TO DO')
        }
        progressJiraIssues {
            jqlSearch('id=${JIRA_KEY}')
            workflowActionName('TO DO')
            comment('Deployed in CGI')
        }
    }

    publishers {
        retryBuild {
            retryLimit(1)
            fixedDelay(10)
        }
    }
}
