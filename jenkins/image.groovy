withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
    sh '''
    commit_hash=$(cat commit_hash.txt)
    branch=$(cat branch.txt)

    image=sccity/santaclarautah

    if [ -z "$commit_hash" ]; then
        echo "Error: Commit Hash File is Missing!"
        exit 1
    fi


    echo "Using Commit Hash: $commit_hash for Docker build"
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

    docker build --platform linux/x86_64 -t $image:$commit_hash-$branch --push .

    if [ $? -ne 0 ]; then
        echo "Error: Docker Latest Tag Push Failed!"
        exit 1
    fi
    '''
}