sh '''
    # Load environment variables from initialize stage
    source env.properties
    
    # Health check for WordPress installation
    echo "Performing health check..."
    
    # Get the image name from the build
    IMAGE_NAME="sccity/santaclarautah:${GIT_COMMIT:0:7}-dev"
    
    # Create WordPress container with database connection
    echo "Starting WordPress container for health check..."
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
    
    # Check file permissions
    echo "Checking file permissions..."
    docker exec $CONTAINER_ID find /var/www/html -type f -exec stat -c "%a %n" {} \\;
    
    # Check PHP configuration
    echo "Checking PHP configuration..."
    docker exec $CONTAINER_ID php -i | grep -E "memory_limit|max_execution_time|upload_max_filesize"
    
    # Check Apache configuration
    echo "Checking Apache configuration..."
    docker exec $CONTAINER_ID apache2ctl -t
    
    # Check WordPress configuration
    echo "Checking WordPress configuration..."
    docker exec $CONTAINER_ID wp config list --path=/var/www/html
    
    # Check plugin compatibility
    echo "Checking plugin compatibility..."
    docker exec $CONTAINER_ID wp plugin list --path=/var/www/html --format=csv --fields=name,status,version,update
    
    # Check theme compatibility
    echo "Checking theme compatibility..."
    docker exec $CONTAINER_ID wp theme list --path=/var/www/html --format=csv --fields=name,status,version,update
    
    # Check PHP error logs
    echo "Checking PHP error logs..."
    docker exec $CONTAINER_ID cat /var/log/php/error.log || true
    
    # Check Apache error logs
    echo "Checking Apache error logs..."
    docker exec $CONTAINER_ID cat /var/log/apache2/error.log || true
    
    # Clean up
    docker rm -f $CONTAINER_ID
    
    echo "Health check complete!"
''' 