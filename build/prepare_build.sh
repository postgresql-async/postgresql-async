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

echo "preparing postgresql configs"

psql -h 127.0.0.1 -d "postgres" -c 'create database netty_driver_test;' -U $PGUSER
psql -h 127.0.0.1 -d "postgres" -c 'create database netty_driver_time_test;' -U $PGUSER
psql -h 127.0.0.1 -d "postgres" -c "alter database netty_driver_time_test set timezone to 'GMT'" -U $PGUSER
psql -h 127.0.0.1 -d "netty_driver_test" -c "create table transaction_test ( id varchar(255) not null, constraint id_unique primary key (id))" -U $PGUSER
psql -h 127.0.0.1 -d "postgres" -c "CREATE USER postgres_md5 WITH PASSWORD 'postgres_md5'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_md5;" -U $PGUSER
psql -h 127.0.0.1 -d "postgres" -c "CREATE USER postgres_cleartext WITH PASSWORD 'postgres_cleartext'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_cleartext;" -U $PGUSER
psql -h 127.0.0.1 -d "postgres" -c "CREATE USER postgres_kerberos WITH PASSWORD 'postgres_kerberos'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_kerberos;" -U $PGUSER
psql -h 127.0.0.1 -d "netty_driver_test" -c "CREATE TYPE example_mood AS ENUM ('sad', 'ok', 'happy');" -U $PGUSER

echo 'Testing pg acccount'
PGPASSWORD=postgres_md5 psql -h 127.0.0.1 -d 'netty_driver_test' -U postgres_md5 -c "SELECT 1"
