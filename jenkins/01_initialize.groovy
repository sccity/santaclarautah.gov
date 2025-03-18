sh '''
    # Set up test environment
    echo "Setting up test environment..."
    
    # Create a network for the containers
    NETWORK_NAME="wordpress_test_network"
    docker network create $NETWORK_NAME
    
    # Start MySQL container
    echo "Starting MySQL container..."
    MYSQL_CONTAINER=$(docker run -d --rm \
        --network $NETWORK_NAME \
        -e MYSQL_ROOT_PASSWORD=test_password \
        -e MYSQL_DATABASE=wordpress \
        -e MYSQL_USER=wordpress \
        -e MYSQL_PASSWORD=wordpress \
        mysql:8.0)
    
    # Wait for MySQL to be ready
    echo "Waiting for MySQL to be ready..."
    until docker exec $MYSQL_CONTAINER mysqladmin ping -h localhost -u root -ptest_password --silent; do
        sleep 1
    done
    
    # Export variables for other stages
    echo "MYSQL_CONTAINER=$MYSQL_CONTAINER" > env.properties
    echo "NETWORK_NAME=$NETWORK_NAME" >> env.properties
'''