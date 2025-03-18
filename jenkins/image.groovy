def call(String imageName, String tag) {
    sh """
        docker build -t ${imageName}:${tag} .
        docker tag ${imageName}:${tag} ${imageName}:latest
    """
} 