sh '''
    # Create necessary directories
    mkdir -p /var/www/html/wp-content/plugins
    mkdir -p /var/www/html/wp-content/uploads
    mkdir -p /var/www/html/wp-content/themes
    
    # Set permissions
    chown -R www-data:www-data /var/www/html
    chmod -R 755 /var/www/html
''' 