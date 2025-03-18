FROM wordpress:6.7.2-php8.3-apache

# Install unzip and curl for plugin installation and health checks
RUN apt-get update && apt-get install -y unzip curl && rm -rf /var/lib/apt/lists/*

# Configure Apache to use port 8080
RUN sed -i 's/Listen 80/Listen 8080/' /etc/apache2/ports.conf && \
    sed -i 's/:80/:8080/' /etc/apache2/sites-enabled/*.conf

# Create necessary directories with correct permissions
RUN mkdir -p /var/www/html/wp-content/plugins && \
    mkdir -p /var/www/html/wp-content/uploads && \
    mkdir -p /var/www/html/wp-content/themes && \
    chown -R www-data:www-data /var/www/html && \
    chmod -R 755 /var/www/html

# Copy plugins and list them for debugging
COPY plugins/*.zip /var/www/html/wp-content/plugins/
RUN ls -la /var/www/html/wp-content/plugins/

# Unzip all plugins and set permissions
RUN cd /var/www/html/wp-content/plugins && \
    for zip in *.zip; do unzip -q "$zip" && rm "$zip"; done && \
    chown -R www-data:www-data /var/www/html/wp-content/plugins

# Copy WordPress core files
RUN cp -a /usr/src/wordpress/. /var/www/html/ && \
    chown -R www-data:www-data /var/www/html && \
    chmod -R 755 /var/www/html

# Ensure wp-config.php is writable by www-data
RUN touch /var/www/html/wp-config.php && \
    chown www-data:www-data /var/www/html/wp-config.php && \
    chmod 644 /var/www/html/wp-config.php

# Set up Apache run directory
RUN mkdir -p /var/run/apache2 && \
    chown -R www-data:www-data /var/run/apache2 && \
    chmod -R 755 /var/run/apache2

# Clean up
RUN apt-get remove -y unzip && apt-get autoremove -y

# Set the working directory
WORKDIR /var/www/html

# Use www-data user
USER www-data

# Expose port 8080
EXPOSE 8080 