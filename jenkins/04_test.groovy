sh '''
    # Test WordPress installation
    curl -f http://localhost/ || exit 1
    
    # Test plugin activation
    wp plugin activate --all --path=/var/www/html || exit 1
    
    # Test theme activation
    wp theme activate twentytwentyfour --path=/var/www/html || exit 1
''' 