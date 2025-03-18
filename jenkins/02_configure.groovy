sh '''
    # Copy plugins to WordPress directory
    cp plugins/*.zip /var/www/html/wp-content/plugins/
    
    # Unzip plugins
    cd /var/www/html/wp-content/plugins
    for zip in *.zip; do
        unzip -q "$zip"
        rm "$zip"
    done
    
    # Set permissions
    chown -R www-data:www-data /var/www/html/wp-content/plugins
''' 