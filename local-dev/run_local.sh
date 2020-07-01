#!/bin/bash
psql -f local-dev/local-postgres-init.sql

export DATABASE_USER=janitoruser
export DATABASE_USER_PASSWORD=janitorpwd
export DATABASE_NAME=janitordb

./gradlew bootRun