pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'sccity'
        APP_NAME = 'santaclarautah'
        NEW_VERSION = '0.0.0' // Default version, will be updated in Prepare Version stage
    }

    triggers {
        // Watch dev branch for changes
        githubPush()
    }

    stages {
        stage('Check Branch') {
            steps {
                script {
                    if (env.BRANCH_NAME != 'dev') {
                        currentBuild.result = 'ABORTED'
                        error('Stopping early: this build should only run on dev branch')
                    }
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Version') {
            steps {
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

        stage('Build and Test') {
            steps {
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

        stage('Plugin Verification') {
            steps {
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

        stage('Push Image') {
            steps {
                script {
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

        stage('Health Check') {
            steps {
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

        stage('Commit Version Update') {
            steps {
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

    post {
        success {
            script {
                echo "Deployment to dev environment successful! New version: ${env.NEW_VERSION}"
                // Clean up Docker images
                sh """
                    docker rmi ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.NEW_VERSION} || true
                """
            }
        }
        failure {
            script {
                echo "Deployment to dev environment failed"
                // Clean up Docker images if they exist
                sh """
                    docker rmi ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.NEW_VERSION} || true
                """
            }
        }
    }
} 