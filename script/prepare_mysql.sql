CREATE DATABASE mysql_async_tests;
CREATE TABLE mysql_async_tests.transaction_test (id VARCHAR(255) NOT NULL, PRIMARY KEY (id));
CREATE USER 'mysql_async'@'localhost' IDENTIFIED BY 'root' WITH GRANT OPTION;
CREATE USER 'mysql_async_old'@'localhost' WITH GRANT OPTION;
CREATE USER 'mysql_async_nopw'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'mysql_async'@'localhost';
GRANT ALL PRIVILEGES ON *.* TO 'mysql_async_old'@'localhost';
UPDATE mysql.user SET Password = OLD_PASSWORD('do_not_use_this'), plugin = 'mysql_old_password' where User = 'mysql_async_old'; flush privileges;
GRANT ALL PRIVILEGES ON *.* TO 'mysql_async_nopw'@'localhost';
