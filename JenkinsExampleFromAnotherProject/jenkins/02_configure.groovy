withCredentials([string(credentialsId: 'fontawesome-npm-token', variable: 'FA_TOKEN')]) {
    sh '''
    echo "@fortawesome:registry=https://npm.fontawesome.com/" >> .npmrc
    echo "//npm.fontawesome.com/:_authToken=${FA_TOKEN}" >> .npmrc

    composer install
    npm install
    npm run build
    '''
}