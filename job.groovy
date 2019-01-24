def curJob = job('ADAMS_DEPLOY_CGI_MERGED') {

    parameters {
        stringParam('DEPLOY_VERSION', '6.5.0-SNAPSHOT', 'Adams version to be deployed')
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
sed  -e "s/\\(JIRA_ISSUES=\\)/\\1issueKey=/" -e "s/ \\(ADAMS-[0-9]\\{4\\}\\)/ or issuekey=\\1/g" -i env.properties
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
