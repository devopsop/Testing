import utilities.Security

def curJob = job('ATHLETECENTRAL_MOBILE_CLIENT_BUILD_IOS') {
    description('Build job for AthleteCentral Mobile IOS')

    // We only keep the last 12 builds
    logRotator {
        numToKeep(12)
    }

    parameters {
        stringParam('BRANCH', 'develop', 'Bitbucket Branch')
        choiceParam('ENVIRONMENT', ['dev','qa-aws','prod','preprod'])
        choiceParam('VERSIONING', ['patch','minor','major','premajor','preminor','prepatch','prerelease'])
        stringParam('CHANNEL', 'test', 'Ionic Channel')
        stringParam('CORE_VERSION', '0.3.10', 'Angular Core Dependencies Version')
        stringParam('APP_ID', '4747195b', 'Ionic APP_ID')
    }

    label('Mac-mini')

    scm {
        git {
            branch('${BRANCH}')
            remote {
                url('https://build_agent@bitbucket.wada-ama.org/scm/adams-ng/athetehub-mobile-client.git')
                //Credentials for the build_agent user
                credentials('003f5c19-50c1-4ae3-a296-f23e630c2bb4')
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
            useAsBuildDisplayName true
        }
    }

    wrappers {
        configFiles {
            // .npmrc file
            file('700b68f6-df2b-4c6a-b8b0-697abc114d2b') {
                targetLocation('.npmrc')
            }
        }
        credentialsBinding {
            usernamePassword('NEXUSCREDS', '003f5c19-50c1-4ae3-a296-f23e630c2bb4')
            string('KEYPASS', 'Keychain-AC-Mac')
        }
        buildUserVars()
    }

    steps {
        shell('''#!/bin/sh

### Using Jenkins NodeJs Plugin, you must first configure ionic login for the proper node installation. This as to be
# done once per node installation.
# 1) Connect to the server as jenkins user.
# 2) cd [jenkins_root]/tools/jenkins.plugins.nodejs.tools.NodeJSInstallation/[NodeInstallationName]/bin/
# 3) ./npm install -g ionic@latest
# 4) ./ionic login
# 5) Follow the steps to authenticate with ssh key

WORKSPACE=`pwd`
BUNDLE_ID="com.nurun.wada.athlete.central"
ionic login $IO_USER $IO_PASS

mkdir ${WORKSPACE}/www

rm -f package-lock.json

ionic link --pro-id ${APP_ID}

cordova plugin add cordova-plugin-ionic@5.2.10 --save \
--variable APP_ID="$APP_ID" \
--variable CHANNEL_NAME="$CHANNEL" \
--variable UPDATE_METHOD="auto"

NEW_VERSION=$(npm --git-tag-version=false version ${VERSIONING})

#Remplace the version number from the config xml : WARNING - only supports the X.X.X format
NEW_VERSION_DIGITS=$(echo ${NEW_VERSION} | cut -d 'v' -f 2)

sed -i.bak  -E s/version=\"[0-9]+\.[0-9]+\.[0-9]+\"/version=\"${NEW_VERSION_DIGITS}\"/g config.xml

#git add package.json
#git add config.xml
#git commit -m "CI Update to version $NEW_VERSION"

#git tag ${NEW_VERSION}
#git push origin master && git push --tags

cd ${WORKSPACE}/private-modules

#rm -rf .gitignore

#fetch nexus packages locally
declare -a modules=("I18n" "AthleteHubApi" "Address" "UI" "Whereabouts" "Date")
for moduleName in "${modules[@]}"
do
  URL="https://nexus.wada-ama.org/repository/npm-WADA-Releases/@WadaAngularCore/${moduleName}/-/${moduleName}-${CORE_VERSION}.tgz"
  echo "Getting: $URL"
  curl -O $URL -u $NEXUSCREDS
done

cd ${WORKSPACE}
for moduleName in "${modules[@]}"
do
  yarn add file:private-modules/${moduleName}-${CORE_VERSION}.tgz --ignore-scripts --network-timeout 100000
done

#remove the npmrc, otherwise we point to the nexus which is not public.
#echo "unsafe-perm=true" > .npmrc

if [ "$BUNDLE_ID" = "com.wada.athlete.central" ]; then
  echo "Changing config files to use com.wada.athlete.central (WADA'S app store bundle id)"

  rm -rf GoogleService-Info.plist
  rm -rf google-services.json

  mv GoogleService-Info.prod.plist GoogleService-Info.plist
  mv google-services.prod.json google-services.json

  sed -i.bak  -E s/id=\".*wada.athlete.central\"/id=\"${BUNDLE_ID}\"/g config.xml
fi

# Custom webpack config does not work properly, so instead we move the configuration as environement.ts. Otherwise when
# doing OAT build both custom config and environment.ts files are included, the latest overriding de first.
export ENV=$IONIC_ENVIRONMENT
npm run generate:env

# Build
IONIC_TOKEN=$(ionic config get -g tokens.user | sed -e s/\'//g )
#npm rebuild node-sass
npm install --quiet --no-optional
npm run build
ionic deploy manifest

cordova platform rm ios
cordova platform add ios

if [[ $IONIC_ENVIRONMENT == "dev" ]] || [[ $IONIC_ENVIRONMENT == "qa-aws" ]] || [[ $IONIC_ENVIRONMENT == "preprod" ]]
then
  echo "EXPORT_METHOD=development" > varfile
  echo "CONFIGURATION=Debug" >> varfile
  echo "PROVISIONING_PROFILE=5a930523-f273-4db9-8a09-53027ab96b02" >> varfile
elif [[ $IONIC_ENVIRONMENT == "prod" ]] 
then
  echo "EXPORT_METHOD=app-store" > varfile
  echo "CONFIGURATION=Release" >> varfile
  echo "PROVISIONING_PROFILE=a94588bb-4302-45b3-9ea7-b351522100a9" >> varfile
fi

# Switch keychain
security list-keychains -s "/var/root/Library/Keychains/AthleteCentralMobile.keychain-db"
security default-keychain -s "/var/root/Library/Keychains/AthleteCentralMobile.keychain-db"
security unlock-keychain -p ${KEYPASS} "/var/root/Library/Keychains/AthleteCentralMobile.keychain-db"
        ''')

        environmentVariables {
            propertiesFile('varfile')
        }

        configure { project ->
            project / steps << 'au.com.rayh.XCodeBuilder' {
                cleanBeforeBuild false
                cleanTestReports false
                configuration '${CONFIGURATION}'
                target
                sdk
                symRoot
                buildDir
                xcodeProjectPath 'platforms/ios'
                xcodeProjectFile
                xcodebuildArguments 'CODE_SIGN_STYLE=&quot;Manual&quot;PROVISIONING_PROFILE=${PROVISIONING_PROFILE}'
                xcodeSchema 'Athlete Central'
                xcodeWorkspaceFile 'Athlete Central'
                cfBundleVersionValue
                cfBundleShortVersionStringValue
                buildIpa true
                ipaExportMethod '${EXPORT_METHOD}'
                generateArchive false
                noConsoleLog false
                logfileOutputDirectory
                unlockKeychain false
                keychainName
                keychainPath
                keychainPwd '{AQAAABAAAAAQQyjOtPVogXIvE1sOI9cpGGyWrDw+2P4i3hiyBC5ZjBs=}'
                developmentTeamName 'World Anti-Doping Agency'
                developmentTeamID
                allowFailingBuildResults false
                ipaName 'AC_${IONIC_ENVIRONMENT}-${RELEASE_BUILD_NUMBER}'
                ipaOutputDirectory '${WORKSPACE}'
                provideApplicationVersion false
                changeBundleID false
                bundleID
                bundleIDInfoPlistPath
                interpretTargetAsRegEx false
                signingMethod 'manual'
                provisioningProfiles
                    <au.com.rayh.ProvisioningProfile>
                    <provisioningProfileAppId>com.wada.athlete.central</provisioningProfileAppId>
                    <provisioningProfileUUID>5a930523-f273-4db9-8a09-53027ab96b02</provisioningProfileUUID>
                    </au.com.rayh.ProvisioningProfile>
                    <au.com.rayh.ProvisioningProfile>
                    <provisioningProfileAppId>com.wada.athlete.central</provisioningProfileAppId>
                    <provisioningProfileUUID>a94588bb-4302-45b3-9ea7-b351522100a9</provisioningProfileUUID>
                    </au.com.rayh.ProvisioningProfile>
                uploadBitcode true
                uploadSymbols true
                compileBitcode true
                thinning
                appURL
                displayImageURL
                fullSizeImageURL
                assetPackManifestURL
                skipBuildStep false
                stripSwiftSymbols true
                copyProvisioningProfile false
                useLegacyBuildSystem false
                resultBundlePath
                cleanResultBundlePath false
            }
        }

        nexusArtifactUploader {
            nexusVersion('nexus3')
            protocol('https')
            nexusUrl('nexus.wada-ama.org')
            groupId('org.wada')
            version('${RELEASE_BUILD_NUMBER}-${VERSION_SUFFIX}')
            repository('${NEXUS_REPO}')
            credentialsId('003f5c19-50c1-4ae3-a296-f23e630c2bb4')
            artifact {
                artifactId('athletecentral-mobile-ipa')
                type('ipa')
                classifier('${ENVIRONMENT}')
                file('AC_${ENVIRONMENT}-${RELEASE_BUILD_NUMBER}.ipa')
            }
        }
    }

    publishers {
        downstreamParameterized {
            trigger('CREATE_JIRA_TICKET_ATHLETE_MOBILE') {
                condition('SUCCESS')
                parameters {
                    predefinedProp('BUILD_NUMBER', '${RELEASE_BUILD_NUMBER}')
                    predefinedProp('BRANCH', '${BRANCH}')
                    predefinedProp('VERSIONING', '${VERSIONING}')
                    predefinedProp('VERSION_NUMBER', '${CORE_VERSION}')
                    predefinedProp('CHANNEL', '${CHANNEL}')
                    predefinedProp('ENVIRONMENT', '${ENVIRONMENT}')
                    predefinedProp('NEXUS_PATH', 'https://nexus.wada-ama.org/repository/${NEXUS_REPO}/org/wada/athletecentral-mobile-ipa/${RELEASE_BUILD_NUMBER}/athletecentral-mobile-ipa-${RELEASE_BUILD_NUMBER}-${ENVIRONMENT}.ipa')
                    predefinedProp('PLATFORM', 'IOS')
                }
            }
        }
    }
}
