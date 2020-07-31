#!/bin/bash

VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0
VAULT_SERVICE_ACCOUNT_PATH=secret/dsde/terra/kernel/integration/toolsalpha/crl_janitor/app-sa
VAULT_CLIENT_SERVICE_ACCOUNT_PATH=secret/dsde/terra/kernel/integration/toolsalpha/crl_janitor/client-sa
SERVICE_ACCOUNT_OUTPUT_FILE_PATH="$(dirname $0)"/src/test/resources/rendered/sa-account.json
CLIENT_SERVICE_ACCOUNT_OUTPUT_FILE_PATH="$(dirname $0)"/src/test/resources/rendered/client-sa-account.json

docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN ${DSDE_TOOLBOX_DOCKER_IMAGE} \
            vault read -format json ${VAULT_SERVICE_ACCOUNT_PATH} \
            | jq -r .data.key | base64 -d > ${SERVICE_ACCOUNT_OUTPUT_FILE_PATH}
docker run --rm --cap-add IPC_LOCK \
            -e VAULT_TOKEN=$VAULT_TOKEN ${DSDE_TOOLBOX_DOCKER_IMAGE} \
            vault read -format json ${VAULT_SERVICE_ACCOUNT_PATH} \
            | jq -r .data.key | base64 -d > ${CLIENT_SERVICE_ACCOUNT_OUTPUT_FILE_PATH}