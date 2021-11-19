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
export AZURE_MANAGED_APP_CLIENT_ID=$(jq -r .client_id src/test/resources/rendered/azure-mananged-app-client.json)
export AZURE_MANAGED_APP_CLIENT_SECRET=$(jq -r .client_secret src/test/resources/rendered/azure-mananged-app-client.json)
export AZURE_MANAGED_APP_TENANT_ID=$(jq -r .tenant_id src/test/resources/rendered/azure-mananged-app-client.json)
./gradlew bootRun
