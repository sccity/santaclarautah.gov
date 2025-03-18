#!/usr/bin/env groovy

// Load stage files
def initialize = load 'jenkins/01_initialize.groovy'
def configure = load 'jenkins/02_configure.groovy'
def prepare = load 'jenkins/03_prepare.groovy'
def test = load 'jenkins/04_test.groovy'
def image = load 'jenkins/image.groovy'
def tag = load 'jenkins/tag.groovy'

// Main pipeline definition
// This pipeline handles WordPress setup, testing, and deployment to dev environment
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
      args: ["--host=tcp://0.0.0.0:2375", "--host=unix:///var/run/docker.sock"]
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
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
    }

    // GitHub webhook trigger configuration
    triggers {
        githubPush()
        // Fallback polling every 5 minutes if webhook fails
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    initialize()
                }
            }
        }

        stage('Configure') {
            steps {
                script {
                    configure()
                }
            }
        }

        stage('Prepare') {
            steps {
                script {
                    prepare()
                }
            }
        }

        stage('Test') {
            steps {
                script {
                    test()
                }
            }
        }

        stage('Build Image') {
            steps {
                script {
                    def imageTag = tag()
                    image('santaclarautah/wordpress', imageTag)
                }
            }
        }

        stage('Push Image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                    sh '''
                        docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
                        docker push santaclarautah/wordpress:latest
                    '''
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
        failure {
            emailext (
                subject: "❌ WordPress Build Failed",
                body: """
                    Build Status: Failed
                    Build URL: ${env.BUILD_URL}
                    
                    Changes in this build:
                    ${currentBuild.changeSets}
                    
                    Console Output:
                    ${currentBuild.getLog()}
                """,
                recipientProviders: [[$class: 'DevelopersRecipientProvider']],
                attachmentsPattern: '**/build.log',
                to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov'
            )
        }
        fixed {
            emailext (
                subject: "✅ WordPress Build Fixed",
                body: """
                    Build Status: Fixed
                    Build URL: ${env.BUILD_URL}
                    
                    Changes in this build:
                    ${currentBuild.changeSets}
                """,
                recipientProviders: [[$class: 'DevelopersRecipientProvider']],
                to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov'
            )
        }
    }
} 