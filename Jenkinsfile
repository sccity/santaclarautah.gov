pipeline {
    agent {
        kubernetes {
            label "${env.JOB_NAME}-${BUILD_NUMBER}"
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: jnlp
      image: sccity/jenkins-agent-php:0.0.5
      volumeMounts:
        - name: workspace-volume
          mountPath: /home/jenkins/agent

    - name: docker
      image: docker:24.0.6-dind
      securityContext:
        privileged: true
      command: ["dockerd-entrypoint.sh"]
      args: ["--host=tcp://0.0.0.0:2375"]
      env:
        - name: DOCKER_TLS_VERIFY
          value: "0"
      volumeMounts:
        - name: docker-lib
          mountPath: /var/lib/docker
        - name: workspace-volume
          mountPath: /home/jenkins/agent

    - name: kubectl
      image: bitnami/kubectl:latest
      command: ['cat']
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: /home/jenkins/agent

  volumes:
    - name: workspace-volume
      emptyDir: {}
    - name: docker-lib
      emptyDir: {}

  nodeSelector:
    Name: jenkins-nodes-k8s-prd-aws-us-west2
  tolerations:
    - key: type
      operator: Equal
      value: jenkins
      effect: NoSchedule
            '''
        }
    }

    environment {
        DOCKER_REGISTRY = 'sccity'
        APP_NAME = 'santaclarautah'
        NEW_VERSION = '0.0.0' // Default version, will be updated in Prepare Version stage
        DOCKER_HOST = 'tcp://localhost:2375'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
    }

    // Add specific triggers for GitHub webhook
    triggers {
        githubPush()
        pollSCM('H/5 * * * *')  // Fallback polling every 5 minutes
    }

    stages {
        stage('Check Branch') {
            steps {
                container('jnlp') {
                    script {
                        sh """
                            git checkout dev
                            git pull origin dev
                        """
                    }
                }
            }
        }

        stage('Prepare Version') {
            steps {
                container('jnlp') {
                    script {
                        // Get current version and increment patch
                        def currentVersion = sh(
                            script: "grep -o 'sccity/santaclarautah:[0-9.]*' k8s-manifests/wordpress-deployment.yaml | cut -d: -f2",
                            returnStdout: true
                        ).trim()

                        // Parse version components
                        def parts = currentVersion.tokenize('.')
                        def major = parts[0]
                        def minor = parts[1]
                        def patch = parts.size() > 2 ? parts[2].toInteger() : 0

                        // Increment patch version
                        env.NEW_VERSION = "${major}.${minor}.${patch + 1}"
                        
                        // Update deployment file
                        sh """
                            sed -i "s|sccity/santaclarautah:${currentVersion}|sccity/santaclarautah:${env.NEW_VERSION}|" k8s-manifests/wordpress-deployment.yaml
                        """
                    }
                }
            }
        }

        stage('Build and Test') {
            steps {
                container('docker') {
                    script {
                        // Build Docker image
                        sh """
                            docker build --platform linux/amd64 -t ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION} .
                        """

                        // Basic test to verify WordPress files exist
                        sh """
                            docker run --rm ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION} test -f /var/www/html/wp-config.php
                            docker run --rm ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION} test -d /var/www/html/wp-content/plugins
                            docker run --rm ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION} test -f /var/www/html/wp-content/plugins/elementor/elementor.php
                        """
                    }
                }
            }
        }

        stage('Plugin Verification') {
            steps {
                container('docker') {
                    script {
                        // Get list of expected plugins from plugins directory
                        def expectedPlugins = sh(
                            script: "ls -1 plugins/*.zip | sed 's/plugins\\///' | sed 's/\\.zip//'",
                            returnStdout: true
                        ).trim().split('\n')

                        // Run container and verify plugins are installed and activated
                        sh """
                            docker run --rm ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION} bash -c '
                                cd /var/www/html/wp-content/plugins && 
                                for plugin in *; do
                                    if [ -d "\$plugin" ]; then
                                        echo "Verifying plugin: \$plugin"
                                        test -f "\$plugin/\$plugin.php" || test -f "\$plugin/index.php"
                                    fi
                                done
                            '
                        """
                    }
                }
            }
        }

        stage('Push Image') {
            steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId: 'docker-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh """
                            echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin
                            docker push ${DOCKER_REGISTRY}/${APP_NAME}:${env.NEW_VERSION}
                        """
                    }
                }
            }
        }

        stage('Deploy to Dev') {
            steps {
                container('kubectl') {
                    withCredentials([file(credentialsId: 'kube-config', variable: 'KUBECONFIG')]) {
                        sh """
                            kubectl apply -f k8s-manifests/apache-config.yaml
                            kubectl apply -f k8s-manifests/wordpress-config.yaml
                            kubectl apply -f k8s-manifests/wordpress-deployment.yaml
                            kubectl rollout status deployment santaclarautah-dev -n webprod
                        """
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                container('kubectl') {
                    withCredentials([file(credentialsId: 'kube-config', variable: 'KUBECONFIG')]) {
                        sh """
                            # Wait for pods to be ready
                            sleep 30
                            
                            # Get pod status
                            kubectl get pods -n webprod -l app=santaclarautah-dev
                            
                            # Check WordPress installation
                            POD=\$(kubectl get pods -n webprod -l app=santaclarautah-dev -o name | head -1)
                            kubectl exec -n webprod \$POD -- curl -s -f http://localhost:8080/
                            
                            # Verify Apache configuration
                            kubectl exec -n webprod \$POD -- apache2ctl -t
                            
                            # Check WordPress permissions
                            kubectl exec -n webprod \$POD -- find /var/www/html -type f -exec stat -c "%a %n" {} \\;
                        """
                    }
                }
            }
        }

        stage('Commit Version Update') {
            steps {
                container('jnlp') {
                    withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                        sh """
                            git config --global user.email "jenkins@santaclarautah.gov"
                            git config --global user.name "Jenkins CI"
                            git add k8s-manifests/wordpress-deployment.yaml
                            git commit -m "chore: update version to ${env.NEW_VERSION} [skip ci]"
                            git push https://\${GIT_USER}:\${GIT_PASS}@github.com/sccity/santaclarautah.gov.git dev
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                container('docker') {
                    sh """
                        docker rmi ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.NEW_VERSION} || true
                    """
                }
            }
        }
        failure {
            script {
                container('docker') {
                    sh """
                        docker rmi ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.NEW_VERSION} || true
                    """
                }
                
                emailext (
                    subject: "❌ Build Failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                        <p>The build failed for ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                        <p><b>Build URL:</b> <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                        <p><b>Console Output (last 100 lines):</b></p>
                        <pre>${currentBuild.rawBuild.getLog(100).join('\n')}</pre>
                    """,
                    to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov',
                    replyTo: 'no-reply@santaclarautah.gov',
                    mimeType: 'text/html',
                    attachLog: true
                )
            }
        }
        unstable {
            script {
                emailext (
                    subject: "⚠️ Build Unstable: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                        <p>The build is unstable for ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                        <p><b>Build URL:</b> <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                    """,
                    to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov',
                    replyTo: 'no-reply@santaclarautah.gov',
                    mimeType: 'text/html'
                )
            }
        }
        fixed {
            script {
                emailext (
                    subject: "✅ Build Fixed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                        <p>The build has been fixed in ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                        <p><b>Build URL:</b> <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                    """,
                    to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov',
                    replyTo: 'no-reply@santaclarautah.gov',
                    mimeType: 'text/html'
                )
            }
        }
    }
} 