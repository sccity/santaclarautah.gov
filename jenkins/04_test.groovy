sh '''
    # Load environment variables from initialize stage
    source env.properties
    
    # Test WordPress installation
    echo "Testing WordPress installation..."
    
    # Get the image name from the build
    IMAGE_NAME="sccity/santaclarautah:${GIT_COMMIT:0:7}-dev"
    
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
    
    # Install WP-CLI
    echo "Installing WP-CLI..."
    docker exec $CONTAINER_ID curl -O https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar
    docker exec $CONTAINER_ID chmod +x wp-cli.phar
    docker exec $CONTAINER_ID mv wp-cli.phar /usr/local/bin/wp
    
    # Test WordPress core
    echo "Testing WordPress core..."
    docker exec $CONTAINER_ID wp core verify-checksums --path=/var/www/html
    
    # Test database connection
    echo "Testing database connection..."
    docker exec $CONTAINER_ID wp db check --path=/var/www/html
    
    # Test plugin installation
    echo "Testing plugin installation..."
    docker exec $CONTAINER_ID wp plugin list --path=/var/www/html --format=csv
    
    # Test theme installation
    echo "Testing theme installation..."
    docker exec $CONTAINER_ID wp theme list --path=/var/www/html --format=csv
    
    # Test HTTP response
    echo "Testing HTTP response..."
    HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080)
    
    if [ "$HTTP_RESPONSE" != "302" ]; then
        echo "Error: WordPress returned HTTP $HTTP_RESPONSE (expected 302 for fresh install)"
        echo "WordPress container logs:"
        docker logs $CONTAINER_ID
        echo "MySQL container logs:"
        docker logs $MYSQL_CONTAINER
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    echo "WordPress installation is accessible and ready for setup (HTTP 302)"
    
    # Clean up WordPress container
    docker rm -f $CONTAINER_ID
    
    echo "All tests passed successfully!"
'''