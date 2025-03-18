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
      volumeMounts:
        - name: docker-lib
          mountPath: /var/lib/docker
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
        IMAGE_TAG = "${GIT_COMMIT[0..6]}-dev"
        IMAGE_NAME = "sccity/santaclarautah:${IMAGE_TAG}"
    }

    stages {
        stage('Initialize') {
            steps {
                container('docker') {
                    load './jenkins/01_initialize.groovy'
                }
            }
        }

        stage('Configure') {
            steps {
                container('jnlp') {
                    load './jenkins/02_configure.groovy'
                }
            }
        }

        stage('Build') {
            steps {
                container('docker') {
                    sh """
                        echo "Building WordPress image..."
                        docker build --platform linux/x86_64 -t ${IMAGE_NAME} .
                    """
                }
            }
        }

        stage('Test') {
            steps {
                container('docker') {
                    load './jenkins/04_test.groovy'
                }
            }
        }
    }

    post {
        success {
            script {
                container('jnlp') {
                    load './jenkins/tag.groovy'
                }

                container('docker') {
                    load './jenkins/image.groovy'
                }

                //container('jnlp') {
                    //load './jenkins/deploy.groovy'
                //}
            }
        }
        fixed {
            load './jenkins/fixed.groovy'
        }
        failure {
            load './jenkins/failure.groovy'
        }
    }
}
