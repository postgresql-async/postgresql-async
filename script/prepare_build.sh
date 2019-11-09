#!/usr/bin/env sh

SCRIPTDIR=`dirname $0`

echo "Current Hosts"
cat /etc/hosts

echo "Preparing MySQL configs"
mysql -u root < ./script/prepare_mysql.sql
mysql -u root -e "CREATE DATABASE codegen_test;"

echo "preparing postgresql configs"

PGUSER=postgres
PGCONF=/etc/postgresql/9.4/main
PGDATA=/var/ramfs/postgresql/9.4/main

psql -d "postgres" -c 'create database netty_driver_test;' -U $PGUSER
psql -d "postgres" -c 'create database netty_driver_time_test;' -U $PGUSER
psql -d "postgres" -c "alter database netty_driver_time_test set timezone to 'GMT'" -U $PGUSER
psql -d "netty_driver_test" -c "create table transaction_test ( id varchar(255) not null, constraint id_unique primary key (id))" -U $PGUSER
psql -d "postgres" -c "CREATE USER postgres_md5 WITH PASSWORD 'postgres_md5'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_md5;" -U $PGUSER
psql -d "postgres" -c "CREATE USER postgres_cleartext WITH PASSWORD 'postgres_cleartext'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_cleartext;" -U $PGUSER
psql -d "postgres" -c "CREATE USER postgres_kerberos WITH PASSWORD 'postgres_kerberos'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_kerberos;" -U $PGUSER
psql -d "netty_driver_test" -c "CREATE TYPE example_mood AS ENUM ('sad', 'ok', 'happy');" -U $PGUSER

sudo chmod 666 $PGCONF/pg_hba.conf
sudo chmod 666 $PGCONF/postgresql.conf


sudo echo "local    all             all                                     trust"    >  $PGCONF/pg_hba.conf
sudo echo "host     all             postgres           127.0.0.1/32         trust"    >> $PGCONF/pg_hba.conf
sudo echo "host     all             postgres_md5       127.0.0.1/32         md5"      >> $PGCONF/pg_hba.conf
sudo echo "host     all             postgres_cleartext 127.0.0.1/32         password" >> $PGCONF/pg_hba.conf

echo "pg_hba.conf is now like"
cat "$PGCONF/pg_hba.conf"

sudo sed -i "s/^ssl_cert_file.*/ssl_cert_file='server.crt'/" $PGCONF/postgresql.conf
sudo sed -i "s/^ssl_key_file.*/ssl_key_file='server.key'/" $PGCONF/postgresql.conf

echo "postgresql.conf ssl settings"
cat "$PGCONF/postgresql.conf"|grep 'ssl'

sudo cp -f $SCRIPTDIR/server.crt $SCRIPTDIR/server.key $PGDATA
sudo chmod 600 $PGCONF/pg_hba.conf
sudo chmod 600 $PGCONF/postgresql.conf


sudo /etc/init.d/postgresql restart 9.4
