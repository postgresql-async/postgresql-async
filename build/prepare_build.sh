#!/usr/bin/env sh

SCRIPTDIR=`dirname $0`

echo "Current Hosts"
cat /etc/hosts
RETRIES=10
until mysqladmin ping -h'127.0.0.1' --silent || [ $RETRIES -eq 0 ]; do
    echo "Waiting mysql server, $((RETRIES--))..."
    sleep 3
done

RETRIES=5
until psql -h $PG_HOST -U postgres -d template1 -c "select 1" > /dev/null 2>&1 || [ $RETRIES -eq 0 ]; do
  echo "Waiting for postgres server, $((RETRIES--))..."
  sleep 3
done


echo "Preparing MySQL configs"
mysql -u root -h 127.0.0.1 < ./build/prepare_mysql.sql
mysql -u root -h 127.0.0.1 -e "CREATE DATABASE codegen_test;"

echo "Testing mysql account"
mysql -u mysql_async -proot -h 127.0.0.1 -e "SELECT 1"

bash $SCRIPTDIR/prepare_pg.sh
