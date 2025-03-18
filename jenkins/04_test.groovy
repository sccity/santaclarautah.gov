sh '''
    # Load environment variables from initialize stage
    if [ ! -f env.properties ]; then
        echo "Error: env.properties not found. Initialize stage must run first."
        exit 1
    fi
    
    source env.properties
    
    # Validate required environment variables
    if [ -z "$MYSQL_CONTAINER" ] || [ -z "$NETWORK_NAME" ] || [ -z "$IMAGE_NAME" ]; then
        echo "Error: Required environment variables not set"
        echo "MYSQL_CONTAINER: $MYSQL_CONTAINER"
        echo "NETWORK_NAME: $NETWORK_NAME"
        echo "IMAGE_NAME: $IMAGE_NAME"
        exit 1
    fi
    
    # Test WordPress installation
    echo "Testing WordPress installation..."
    
    # Create WordPress container with database connection
    echo "Starting WordPress container..."
    
    # Create wp-config.php with database settings
    docker exec $MYSQL_CONTAINER mysql -uwordpress -pwordpress -e "CREATE DATABASE IF NOT EXISTS wordpress;"
    
    # Create a temporary wp-config.php file
    cat > wp-config.php << 'EOL'
<?php
define('DB_NAME', 'wordpress');
define('DB_USER', 'wordpress');
define('DB_PASSWORD', 'wordpress');
define('DB_HOST', 'mysql');
define('DB_CHARSET', 'utf8');
define('DB_COLLATE', '');
define('WP_DEBUG', true);
EOL
    
    CONTAINER_ID=$(docker create --rm \
        --network wordpress_test_network \
        -p 8080:80 \
        -e WORDPRESS_DB_HOST=mysql \
        -e WORDPRESS_DB_NAME=wordpress \
        -e WORDPRESS_DB_USER=wordpress \
        -e WORDPRESS_DB_PASSWORD=wordpress \
        ${IMAGE_NAME})
    
    if [ -z "$CONTAINER_ID" ]; then
        echo "Error: Failed to create WordPress container"
        exit 1
    fi
    
    # Start the container first
    docker start $CONTAINER_ID
    
    # Wait a moment for the container to be fully running
    sleep 2
    
    # Copy wp-config.php into the container
    docker cp wp-config.php $CONTAINER_ID:/var/www/html/wp-config.php
    docker exec $CONTAINER_ID chown www-data:www-data /var/www/html/wp-config.php
    docker exec $CONTAINER_ID chmod 644 /var/www/html/wp-config.php
    
    # Wait for Apache to start with timeout
    echo "Waiting for Apache to start..."
    TIMEOUT=30
    COUNTER=0
    while ! docker exec $CONTAINER_ID curl -s http://localhost >/dev/null; do
        sleep 1
        COUNTER=$((COUNTER + 1))
        if [ $COUNTER -ge $TIMEOUT ]; then
            echo "Error: Apache failed to start within $TIMEOUT seconds"
            echo "WordPress container logs:"
            docker logs $CONTAINER_ID
            echo "MySQL container logs:"
            docker logs $MYSQL_CONTAINER
            docker rm -f $CONTAINER_ID
            exit 1
        fi
    done
    
    # Install WP-CLI
    echo "Installing WP-CLI..."
    if ! docker exec $CONTAINER_ID curl -s -o /var/www/html/wp-cli.phar https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar; then
        echo "Error: Failed to download WP-CLI"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    docker exec $CONTAINER_ID chmod +x /var/www/html/wp-cli.phar
    
    # Test WordPress core
    echo "Testing WordPress core..."
    if ! docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar core verify-checksums --path=/var/www/html; then
        echo "Error: WordPress core verification failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Test database connection
    echo "Testing database connection..."
    if ! docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar db check --path=/var/www/html; then
        echo "Error: Database connection test failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Test plugin installation
    echo "Testing plugin installation..."
    if ! docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar plugin list --path=/var/www/html --format=csv; then
        echo "Error: Plugin list command failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Test theme installation
    echo "Testing theme installation..."
    if ! docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar theme list --path=/var/www/html --format=csv; then
        echo "Error: Theme list command failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Test HTTP response
    echo "Testing HTTP response..."
    HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080)
    
    if [ "$HTTP_RESPONSE" != "302" ]; then
        echo "Error: WordPress returned HTTP $HTTP_RESPONSE (expected 302 for fresh install)"
        echo "WordPress container logs:"
        docker logs $CONTAINER_ID
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    echo "WordPress installation is accessible and ready for setup (HTTP 302)"
    
    # Clean up WordPress container
    docker rm -f $CONTAINER_ID
    
    echo "All tests passed successfully!"
'''