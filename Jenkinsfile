def SERVICES = ['inventory-service', 'order-service', 'api-gateway']

pipeline {
    agent any

    tools {
        jdk 'jdk17'
        maven 'maven3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        DOCKERHUB_NAMESPACE = 'karnativ'
        DOCKERHUB_CREDS     = 'dockerhub-credentials'
        IMAGE_TAG         = "1.0.${BUILD_NUMBER}"
        SONARQUBE_ENV     = 'sonarqube'
        SONAR_ORGANIZATION = 'karnativishnu'
        SONAR_MAVEN_PLUGIN = 'org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922'
        SONAR_EXCLUSIONS   = '**/target/**,**/generated/**'
        K8S_NAMESPACE     = 'microservices-demo'
        KUBECONFIG_CREDS  = 'kubeconfig-microservices-demo'
        TRIVY_EXIT_CODE   = '1'
        TRIVY_SEVERITY    = 'HIGH,CRITICAL'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    currentBuild.displayName = "#${BUILD_NUMBER} ${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    parallel SERVICES.collectEntries { svc ->
                        ["${svc}": {
                            dir(svc) {
                                sh 'mvn -B -ntp clean package -DskipTests'
                            }
                        }]
                    }
                }
            }
        }

        stage('Test') {
            steps {
                script {
                    parallel SERVICES.collectEntries { svc ->
                        ["${svc}": {
                            dir(svc) {
                                sh 'mvn -B -ntp verify'
                            }
                        }]
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '*/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Code Quality') {
            parallel {
                stage('Formatting') {
                    steps {
                        script {
                            SERVICES.each { svc ->
                                dir(svc) {
                                    // Fails the build on unformatted code; run `mvn spotless:apply` to fix.
                                    sh 'mvn -B -ntp spotless:check'
                                }
                            }
                        }
                    }
                }
                stage('Lint') {
                    steps {
                        script {
                            SERVICES.each { svc ->
                                dir(svc) {
                                    sh 'mvn -B -ntp checkstyle:checkstyle'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            recordIssues(
                                enabledForFailure: true,
                                tools: [checkStyle(pattern: '*/target/checkstyle-result.xml')],
                                qualityGates: [[threshold: 50, type: 'TOTAL_HIGH', unstable: true]]
                            )
                        }
                    }
                }
            }
        }

        /*
         * Enable after SonarCloud can reach the Jenkins webhook endpoint.
         * The Quality Gate stage requires that webhook to avoid timing out.
         *
         * stage('SonarQube Analysis') {
         *     tools {
         *         jdk 'jdk21'
         *     }
         *     steps {
         *         withSonarQubeEnv("${SONARQUBE_ENV}") {
         *             script {
         *                 SERVICES.each { svc ->
         *                     dir(svc) {
         *                         sh """
         *                             mvn -B -ntp ${SONAR_MAVEN_PLUGIN}:sonar \\
         *                               -Dsonar.organization=${SONAR_ORGANIZATION} \\
         *                               -Dsonar.projectKey=karnativishnu_${svc} \\
         *                               -Dsonar.projectName=${svc} \\
         *                               -Dsonar.projectVersion=${IMAGE_TAG} \\
         *                               -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \\
         *                               -Dsonar.exclusions=${SONAR_EXCLUSIONS}
         *                         """
         *                     }
         *                 }
         *             }
         *         }
         *     }
         * }
         *
         * stage('Quality Gate') {
         *     steps {
         *         timeout(time: 10, unit: 'MINUTES') {
         *             waitForQualityGate abortPipeline: true
         *         }
         *     }
         * }
         */

        stage('Dependency Vulnerability Scan') {
            steps {
                sh '''
                    trivy fs \
                      --scanners vuln,secret,misconfig \
                      --severity "$TRIVY_SEVERITY" \
                      --exit-code "$TRIVY_EXIT_CODE" \
                      --ignore-unfixed \
                      --format table \
                      --output trivy-fs-report.txt \
                      .
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-fs-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Build Image') {
            steps {
                script {
                    parallel SERVICES.collectEntries { svc ->
                        ["${svc}": {
                            sh """
                                docker build \
                                  -t ${DOCKERHUB_NAMESPACE}/${svc}:${IMAGE_TAG} \
                                  -t ${DOCKERHUB_NAMESPACE}/${svc}:latest \
                                  ./${svc}
                            """
                        }]
                    }
                }
            }
        }

        stage('Image Vulnerability Scan') {
            steps {
                script {
                    SERVICES.each { svc ->
                        sh """
                            trivy image \
                              --severity "\$TRIVY_SEVERITY" \
                              --exit-code "\$TRIVY_EXIT_CODE" \
                              --ignore-unfixed \
                              --format table \
                              --output trivy-image-${svc}.txt \
                              ${DOCKERHUB_NAMESPACE}/${svc}:${IMAGE_TAG}
                        """
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-image-*.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKERHUB_CREDS}",
                    usernameVariable: 'DOCKERHUB_USER',
                    passwordVariable: 'DOCKERHUB_TOKEN'
                )]) {
                    sh 'echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin'
                }
                script {
                    SERVICES.each { svc ->
                        sh """
                            docker push ${DOCKERHUB_NAMESPACE}/${svc}:${IMAGE_TAG}
                            docker push ${DOCKERHUB_NAMESPACE}/${svc}:latest
                        """
                    }
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                withCredentials([file(credentialsId: "${KUBECONFIG_CREDS}", variable: 'KUBECONFIG')]) {
                    sh 'kubectl apply -R -f k8s/'
                    script {
                        SERVICES.each { svc ->
                            sh """
                                kubectl set image deployment/${svc} \
                                                                    ${svc}=${DOCKERHUB_NAMESPACE}/${svc}:${IMAGE_TAG} \
                                  -n ${K8S_NAMESPACE}
                                kubectl rollout status deployment/${svc} \
                                  -n ${K8S_NAMESPACE} --timeout=180s
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                SERVICES.each { svc ->
                    sh "docker rmi ${DOCKERHUB_NAMESPACE}/${svc}:${IMAGE_TAG} ${DOCKERHUB_NAMESPACE}/${svc}:latest || true"
                }
            }
            cleanWs()
        }
        failure {
            echo "Build ${BUILD_NUMBER} failed. Rolling back is manual: kubectl rollout undo deployment/<svc> -n ${K8S_NAMESPACE}"
        }
    }
}
