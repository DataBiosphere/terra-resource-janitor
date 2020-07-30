#!/bin/bash

VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
VAULT_ADDR=https://clotho.broadinstitute.org:8200
VAULT_SERVICE_ACCOUNT_ADMIN_PATH=secret/dsde/terra/kernel/integration/yyu/crl_janitor/app-sa
DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0
SERVICE_ACCOUNT_OUTPUT_FILE_PATH="$(dirname $0)"/src/main/resources/generated/sa-account.json

docker run --rm --cap-add IPC_LOCK \
            -e "VAULT_TOKEN=${{ steps.vault-token-step.outputs.vault-token }}" \
            -e "VAULT_ADDR=${VAULT_ADDR}" \
            vault:1.1.0 \
            vault read -format json $VAULT_SERVICE_ACCOUNT_ADMIN_PATH \
            | jq .data > $SERVICE_ACCOUNT_OUTPUT_FILE_PATH
