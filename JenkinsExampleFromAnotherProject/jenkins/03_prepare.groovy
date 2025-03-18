sh '''
cp .env.example .env
php artisan key:generate
sed -i 's/^LOG_LEVEL=.*/LOG_LEVEL=debug/' .env
sed -i 's/^SCOUT_DRIVER=.*/SCOUT_DRIVER=database/' .env
sed -i 's/^DB_HOST=.*/DB_HOST=localhost/' .env
sed -i 's/^DB_DATABASE=.*/DB_DATABASE=influence360/' .env
sed -i 's/^DB_USERNAME=.*/DB_USERNAME=root/' .env
sed -i 's/^DB_PASSWORD=.*/DB_PASSWORD=/' .env
'''