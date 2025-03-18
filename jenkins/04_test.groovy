sh '''
    # Load environment variables from initialize stage
    source env.properties
    
    # Test WordPress installation
    echo "Testing WordPress installation..."
    
    # Get the image name from the build
    IMAGE_NAME="sccity/santaclarautah:${GIT_COMMIT:0:7}-dev"
    
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
    
    # Create WordPress container with database connection
    echo "Starting WordPress container..."
    CONTAINER_ID=$(docker create --rm \
        --network $NETWORK_NAME \
        -p 8080:8080 \
        -e WORDPRESS_DB_HOST=mysql \
        -e WORDPRESS_DB_NAME=wordpress \
        -e WORDPRESS_DB_USER=wordpress \
        -e WORDPRESS_DB_PASSWORD=wordpress \
        $IMAGE_NAME)
    
    docker start $CONTAINER_ID
    
    # Wait for Apache to start
    echo "Waiting for Apache to start..."
    sleep 5
    
    # Test HTTP response - should be 302 (redirect to install page) for fresh WordPress
    echo "Testing HTTP response..."
    HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080)
    
    if [ "$HTTP_RESPONSE" != "302" ]; then
        echo "Error: WordPress returned HTTP $HTTP_RESPONSE (expected 302 for fresh install)"
        echo "WordPress container logs:"
        docker logs $CONTAINER_ID
        echo "MySQL container logs:"
        docker logs $MYSQL_CONTAINER
        docker rm -f $CONTAINER_ID $MYSQL_CONTAINER
        docker network rm $NETWORK_NAME
        exit 1
    fi
    
    echo "WordPress installation is accessible and ready for setup (HTTP 302)"
    
    # Clean up
    docker rm -f $CONTAINER_ID $MYSQL_CONTAINER
    docker network rm $NETWORK_NAME
    
    echo "All tests passed successfully!"
'''