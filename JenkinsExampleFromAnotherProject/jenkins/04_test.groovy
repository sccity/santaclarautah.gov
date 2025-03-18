sh '''
php artisan migrate:fresh
php artisan db:seed
./vendor/bin/pest

if [ $? -eq 0 ]; then
    ./clean.sh
fi

rm -fR .env
'''