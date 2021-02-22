CREATE DATABASE janitor_db;
CREATE DATABASE janitor_stairway;
CREATE ROLE janitor_user WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
CREATE ROLE janitor_user_stairway WITH LOGIN ENCRYPTED PASSWORD 'dbpwd_stairway';
