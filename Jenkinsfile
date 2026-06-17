pipeline {
  agent any

  environment {
    VERSION = ''
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    disableConcurrentBuilds(abortPrevious: true)
    timeout(time: 30, unit: 'MINUTES')
    timestamps()
  }

  stages {

    stage('Determine Version') {
      steps {
        script {
          VERSION = sh(script: "./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()
          echo "Version: ${VERSION}"
          currentBuild.displayName = "${currentBuild.displayName}-${VERSION}"
        }
      }
    }

    stage('Extract M2 HOME to environment') {
      steps {
        script {
          env.HOST_M2 = sh(script: 'echo $HOME', returnStdout: true).trim() + '/.m2'
        }
      }
    }

    stage('Verify') {
      when { expression { return env.TAG_NAME == null } }
      parallel {

        stage('Test') {
          agent {
            docker {
              image 'mcr.microsoft.com/playwright/java:v1.49.0-jammy'
              reuseNode true
              args "-v ${env.HOST_M2}:/home/jenkins/.m2 -e MAVEN_USER_HOME=/home/jenkins/.m2 -e MAVEN_OPTS='-Dmaven.repo.local=/home/jenkins/.m2/repository'"
            }
          }
          steps {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
              sh './mvnw -B verify -Ddependency-check.skip=true -Dmaven.test.failure.ignore=true'
            }
          }
          post {
            always {
              junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
              recordCoverage(
                tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
                sourceDirectories: [[path: 'src/main/java']]
              )
            }
          }
        }

        stage('Dependency Check') {
          steps {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
              withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                sh './mvnw -B dependency-check:check -DnvdApiKey=$NVD_API_KEY -DfailBuildOnCVSS=11'
              }
            }
          }
          post {
            always {
              archiveArtifacts allowEmptyArchive: true, artifacts: '**/dependency-check-report.*'
              dependencyCheckPublisher(
                pattern: '**/dependency-check-report.xml',
                unstableTotalCritical: 1,
                unstableTotalHigh: 3
              )
            }
          }
        }

      }
    }

    stage('Deploy Artifacts') {
      when {
        anyOf {
          expression { return VERSION.endsWith('-SNAPSHOT') }
          expression { return env.TAG_NAME != null }
        }
      }
      steps {
        configFileProvider([configFile(fileId: 'madgik-settings', variable: 'MAVEN_SETTINGS')]) {
          sh './mvnw deploy -s "$MAVEN_SETTINGS" -B -DskipTests -Ddependency-check.skip=true'
        }
      }
    }

    stage('Handle Releases') {
      when {
        allOf {
          branch 'main'
          not { changeRequest() }
        }
      }
      steps {
        lock(resource: "release-document-analyzer") {
          retry(5) {
            script {
              try {
                withCredentials([string(credentialsId: 'jenkins-github-pat', variable: 'GH_TOKEN')]) {
                  sh '''
                    [ -f /etc/profile.d/load_nvm.sh ] || { echo "ERROR: /etc/profile.d/load_nvm.sh not found. NVM is required on this agent."; exit 1; }
                    { set +x; } 2>/dev/null
                    . /etc/profile.d/load_nvm.sh
                    nvm install --lts
                    set -x
                    npx release-please@17 github-release --repo-url ${GIT_URL} --token ${GH_TOKEN}

                    npx release-please@17 release-pr --repo-url ${GIT_URL} --token ${GH_TOKEN}
                  '''
                }
              } catch (e) {
                sleep time: 45, unit: 'SECONDS'
                throw e
              }
            }
          }
        }
      }
    }

  }

}
