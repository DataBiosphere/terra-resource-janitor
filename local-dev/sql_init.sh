#!/bin/bash

export PGPASSWORD=dbpwd
psql --username dbuser --no-password --host=postgres --port=5432 -d testdb -c "SELECT VERSION();SELECT NOW()"
