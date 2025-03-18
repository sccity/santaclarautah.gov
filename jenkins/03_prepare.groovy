sh '''
    # Copy WordPress files
    cp -a /usr/src/wordpress/. /var/www/html/
    
    # Create wp-config.php
    touch /var/www/html/wp-config.php
    chown www-data:www-data /var/www/html/wp-config.php
    chmod 644 /var/www/html/wp-config.php
    
    # Set permissions
    chown -R www-data:www-data /var/www/html
    chmod -R 755 /var/www/html
''' 