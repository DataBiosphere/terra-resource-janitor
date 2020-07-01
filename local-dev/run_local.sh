#!/bin/bash
export DATABASE_USER=dbuser
export DATABASE_USER_PASSWORD=dbpwd
export DATABASE_NAME=testdb

psql -f local-dev/local-postgres-init.sql

./gradlew bootRun