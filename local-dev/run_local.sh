#!/bin/bash

export DATABASE_USER=dbuser
export DATABASE_USER_PASSWORD=dbpwd
export DATABASE_NAME=testdb
export STAIRWAY_DATABASE_USER=dbuser-stairway
export STAIRWAY_DATABASE_USER_PASSWORD=dbpwd-stairway
export STAIRWAY_DATABASE_NAME=testdb-stairway

./gradlew bootRun