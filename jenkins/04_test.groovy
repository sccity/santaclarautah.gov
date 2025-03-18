sh '''
    # Test WordPress installation
    echo "Testing WordPress installation..."
    
    # Check if WordPress files exist
    if [ ! -f /var/www/html/wp-config.php ]; then
        echo "Error: wp-config.php not found"
        exit 1
    fi
    
    # Check if plugins directory exists and has correct permissions
    if [ ! -d /var/www/html/wp-content/plugins ]; then
        echo "Error: plugins directory not found"
        exit 1
    fi
    
    # Verify plugin installation
    echo "Verifying plugins..."
    # Get list of plugin directories
    PLUGIN_DIRS=$(ls -d /var/www/html/wp-content/plugins/*/ 2>/dev/null | sed "s/\\/\\//g" | sed "s/.*\\/plugins\\///" | sed "s/\\///")
    
    if [ -z "$PLUGIN_DIRS" ]; then
        echo "Error: No plugins found in plugins directory"
        exit 1
    fi
    
    # Check each plugin directory
    for plugin in $PLUGIN_DIRS; do
        if [ ! -d "/var/www/html/wp-content/plugins/$plugin" ]; then
            echo "Error: Plugin directory $plugin not found"
            exit 1
        fi
        echo "Verified plugin: $plugin"
    done
    
    # Check file permissions
    echo "Checking file permissions..."
    if [ "$(stat -c '%U:%G' /var/www/html)" != "www-data:www-data" ]; then
        echo "Error: Incorrect ownership on WordPress root directory"
        exit 1
    fi
    
    # Test Apache configuration
    echo "Testing Apache configuration..."
    apache2ctl configtest
    
    # Test PHP
    echo "Testing PHP..."
    php -v
    
    # Test WordPress CLI if available
    if command -v wp &> /dev/null; then
        echo "Testing WordPress CLI..."
        wp core version
    fi
    
    echo "All tests passed successfully!"
'''