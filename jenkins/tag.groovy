def call() {
    def tag = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    return tag
} 