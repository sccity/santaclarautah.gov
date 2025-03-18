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
    
    # Create a temporary wp-config.php file with complete WordPress configuration
    cat > wp-config.php << 'EOL'
<?php
define('DB_NAME', 'wordpress');
define('DB_USER', 'wordpress');
define('DB_PASSWORD', 'wordpress');
define('DB_HOST', 'mysql');
define('DB_CHARSET', 'utf8');
define('DB_COLLATE', '');
define('WP_DEBUG', true);

$table_prefix = 'wp_';

if (!defined('ABSPATH')) {
    define('ABSPATH', __DIR__ . '/');
}

require_once ABSPATH . 'wp-settings.php';
EOL
    
    # Set permissions on wp-config.php before copying
    chmod 644 wp-config.php
    
    # Create the container with the wp-config.php file mounted
    CONTAINER_ID=$(docker create --rm \
        --network wordpress_test_network \
        -p 8080:80 \
        -e WORDPRESS_DB_HOST=mysql \
        -e WORDPRESS_DB_NAME=wordpress \
        -e WORDPRESS_DB_USER=wordpress \
        -e WORDPRESS_DB_PASSWORD=wordpress \
        -v $(pwd)/wp-config.php:/var/www/html/wp-config.php \
        ${IMAGE_NAME})
    
    if [ -z "$CONTAINER_ID" ]; then
        echo "Error: Failed to create WordPress container"
        exit 1
    fi
    
    # Start the container first
    docker start $CONTAINER_ID
    
    # Check container status
    CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' $CONTAINER_ID)
    if [ "$CONTAINER_STATUS" != "running" ]; then
        echo "Error: Container failed to start. Status: $CONTAINER_STATUS"
        echo "Container logs:"
        docker logs $CONTAINER_ID
        echo "Container details:"
        docker inspect $CONTAINER_ID
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Wait a moment for the container to be fully running
    echo "Waiting for container to initialize..."
    sleep 5
    
    # Check container health
    echo "Checking container health..."
    if ! docker exec $CONTAINER_ID ps aux | grep -q apache2; then
        echo "Error: Apache is not running in container"
        echo "Container logs:"
        docker logs $CONTAINER_ID
        echo "Container processes:"
        docker exec $CONTAINER_ID ps aux
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Verify wp-config.php exists and has correct permissions
    echo "Verifying wp-config.php..."
    if ! docker exec $CONTAINER_ID test -f /var/www/html/wp-config.php; then
        echo "Error: wp-config.php is missing in container"
        echo "Container files:"
        docker exec $CONTAINER_ID ls -la /var/www/html/
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Check wp-config.php permissions
    PERMS=$(docker exec $CONTAINER_ID stat -c "%a" /var/www/html/wp-config.php)
    if [ "$PERMS" != "644" ]; then
        echo "Error: wp-config.php has incorrect permissions: $PERMS"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Wait for Apache to start with timeout and better error reporting
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
            echo "WordPress container status:"
            docker inspect $CONTAINER_ID
            echo "WordPress container processes:"
            docker exec $CONTAINER_ID ps aux
            echo "WordPress container files:"
            docker exec $CONTAINER_ID ls -la /var/www/html/
            echo "MySQL container logs:"
            docker logs $MYSQL_CONTAINER
            docker rm -f $CONTAINER_ID
            exit 1
        fi
        if [ $((COUNTER % 5)) -eq 0 ]; then
            echo "Still waiting for Apache to start... ($COUNTER seconds)"
            echo "Current container logs:"
            docker logs --tail 20 $CONTAINER_ID
            echo "Current container processes:"
            docker exec $CONTAINER_ID ps aux
        fi
    done
    
    # Test database connection before proceeding
    echo "Testing database connection..."
    if ! docker exec $CONTAINER_ID mysql -h mysql -u wordpress -pwordpress -e "SELECT 1;" >/dev/null 2>&1; then
        echo "Error: Cannot connect to MySQL database"
        echo "MySQL container logs:"
        docker logs $MYSQL_CONTAINER
        echo "WordPress container logs:"
        docker logs $CONTAINER_ID
        echo "WordPress container network:"
        docker network inspect $NETWORK_NAME
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
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
    
    # Test plugin installation and activation
    echo "Testing plugin installation..."
    PLUGINS=$(docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar plugin list --path=/var/www/html --format=csv)
    if [ $? -ne 0 ]; then
        echo "Error: Plugin list command failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Verify required plugins are installed
    REQUIRED_PLUGINS=(
        "elementor"
        "elementor-pro"
        "formidable"
        "wp-mail-smtp"
        "wp-optimize"
        "wp-fastest-cache"
        "updraftplus"
        "cloudflare"
        "query-monitor"
    )
    
    for plugin in "${REQUIRED_PLUGINS[@]}"; do
        if ! echo "$PLUGINS" | grep -q "$plugin"; then
            echo "Error: Required plugin '$plugin' is not installed"
            docker rm -f $CONTAINER_ID
            exit 1
        fi
    done
    
    # Test theme installation
    echo "Testing theme installation..."
    THEMES=$(docker exec $CONTAINER_ID php /var/www/html/wp-cli.phar theme list --path=/var/www/html --format=csv)
    if [ $? -ne 0 ]; then
        echo "Error: Theme list command failed"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Verify required themes are installed
    REQUIRED_THEMES=("twentytwentyfour")
    for theme in "${REQUIRED_THEMES[@]}"; do
        if ! echo "$THEMES" | grep -q "$theme"; then
            echo "Error: Required theme '$theme' is not installed"
            docker rm -f $CONTAINER_ID
            exit 1
        fi
    done
    
    # Test file permissions
    echo "Testing file permissions..."
    PERMISSIONS=$(docker exec $CONTAINER_ID ls -la /var/www/html/wp-content)
    if [ $? -ne 0 ]; then
        echo "Error: Failed to check file permissions"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Verify uploads directory exists and is writable
    if ! docker exec $CONTAINER_ID test -w /var/www/html/wp-content/uploads; then
        echo "Error: Uploads directory is not writable"
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    # Test wp-config.php settings
    echo "Testing wp-config.php configuration..."
    if ! docker exec $CONTAINER_ID php -r "
        require '/var/www/html/wp-config.php';
        if (!defined('DB_NAME') || !defined('DB_USER') || !defined('DB_HOST')) {
            exit(1);
        }
    "; then
        echo "Error: wp-config.php is missing required constants"
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
    
    # Test for exposed sensitive files
    echo "Testing for exposed sensitive files..."
    SENSITIVE_FILES=(
        "/var/www/html/wp-config.php"
        "/var/www/html/.htaccess"
        "/var/www/html/wp-content/debug.log"
    )
    
    for file in "${SENSITIVE_FILES[@]}"; do
        if docker exec $CONTAINER_ID test -f "$file"; then
            PERMS=$(docker exec $CONTAINER_ID stat -c "%a" "$file")
            if [ "$PERMS" != "644" ] && [ "$PERMS" != "600" ]; then
                echo "Error: Sensitive file '$file' has incorrect permissions: $PERMS"
                docker rm -f $CONTAINER_ID
                exit 1
            fi
        fi
    done
    
    echo "WordPress installation is accessible and ready for setup (HTTP 302)"
    
    # Clean up WordPress container
    docker rm -f $CONTAINER_ID
    
    echo "All tests passed successfully!"
'''