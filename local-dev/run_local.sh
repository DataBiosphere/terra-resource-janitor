#!/bin/bash

export DATABASE_USER=dbuser
export DATABASE_USER_PASSWORD=dbpwd
export DATABASE_NAME=testdb
export JANITOR_STACKDRIVER_ENABLED=false
export STAIRWAY_DATABASE_USER=dbuser_stairway
export STAIRWAY_DATABASE_USER_PASSWORD=dbpwd_stairway
export STAIRWAY_DATABASE_NAME=testdb_stairway
export TRACK_RESOURCE_PUBSUB_ENABLED=false
export CONFIG_BASED_AUTHZ_ENABLED=false
export GOOGLE_APPLICATION_CREDENTIALS=rendered/sa-account.json
./gradlew bootRun
