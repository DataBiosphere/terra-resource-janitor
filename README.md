# terra-resource-janitor
Janitor service to cleanup resources created by [Cloud Resource Library (CRL)](https://github.com/DataBiosphere/terra-cloud-resource-lib) 

## Overview
![](https://app.lucidchart.com/publicSegments/view/057ef338-e869-4fb3-acd9-6ad2d7d560ba/image.jpeg)

1. When a CRL client configures it in cleanup mode, when a cloud resource is about to be created,
CRL will publish a message to the Janitor's pubsub with the cloud resource's unique id.
2. The Janitor Service subscribes to pub/sub, persisting tracking received resources for eventual
cleanup.
3. Once the resource has expired, the Janitor Service uses Stairway to delete the cloud resource. 

## Primay & Secondary Instances
The Janitor is expected to be deployed with one primary instance and 0-many secondary instances.
The primary instance controls the lifecycle of tracked resources. Having a single primary instance
live at a given time makes it easier to reason about concurrency. Only actions that should not be
done by multiple instances should be confined to the primary.

# Development

## Prerequisites
Follow [this instruction](https://adoptopenjdk.net/installation.html) to install AdoptOpenJDK Java 11. 
Here's an easy way on Mac, using [jEnv](https://www.jenv.be/) to manage the active version.

## Configs Rendering
Local Testing and Github Action tests require credentials to be able to call GCP, run
``` local-dev/render-config.sh``` first for local testing. It generates:
* A Google Service Account Secret to create/delete cloud resources in test.
* A Google Service Account Secret to publish message to 'prod' Janitor instance.
* A Google Service Account Secret to publish message to 'test' Janitor instance.

## Run Locally
Set executable permissions:
```
chmod +x gradlew
```

To spin up the local postgres, run:
```
local-dev/run_postgres.sh start
```
Start local server
```
local-dev/run_local.sh
```
And then check http://127.0.0.1:8080/status for service status.

Swagger in local server: http://127.0.0.1:8080/swagger-ui.html

You can connect to local janitor DB by running: 
```
psql postgresql://127.0.0.1:5432/testdb -U dbuser
```
enter `dbpwd` as password

To connect to stairway db, run: 
```
psql postgresql://127.0.0.1:5432/testdb_stairway -U dbuser_stairway
```
enter `dbpwd_stairway` as password

## Deploy to GKE cluster:
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure the following tools are installed on your local machine:
 * Skaffold (https://skaffold.dev/)
 * Helm (https://helm.sh/docs/intro/install/)

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
local-dev/setup_gke_deploy.sh <environment>
```

where `environment` is your personal environment (e.g. `gjordan`) or an existing Terra env (e.g. `toolsalpha`).
You can now push to the specified environment by running

```
skaffold run
```

## Connecting psql client using the Cloud SQL Proxy:
Follow [Installing this instruction](https://cloud.google.com/sql/docs/mysql/sql-proxy#macos-64-bit)
to install Cloud SQL Proxy 

Go to cloud console to get the instance name you want to connect to, then start the proxy:
```
./cloud_sql_proxy -instances=<INSTANCE_CONNECTION_NAME>=tcp:5432
```
Start the client session
```
psql "host=127.0.0.1 sslmode=disable dbname=<DB_NAME> user=<USER_NAME>"
```
For Broad engineer, DB_NAME and USER_NAME can be found in vault. 
```
docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read secret/dsde/terra/kernel/integration/{$NAMESPACE}/crl_janitor/postgres/{db-creds|stairway-db-creds}
```
The db instance name can be also found under `...crl_janitor/postgres/instance` in vault.

Note that you must stop the local postgres first to free the 5432 port.
See [this document](https://cloud.google.com/sql/docs/postgres/connect-admin-proxy) for more details.

### Dependencies
We use [Gradle's dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
to ensure that builds use the same transitive dependencies, so they're reproducible. This means that
adding or updating a dependency requires telling Gradle to save the change. If you're getting errors
that mention "dependency lock state" after changing a dep, you need to do this step.

```sh
./gradlew dependencies --write-locks
```

## Testing

### Unit tests

Spin up the local postgres:
```
local-dev/run_postgres.sh start
```

Then run unit tests:
```
./gradlew test
```
Stop the local postgres:
```
local-dev/run_postgres.sh stop
```
## Authorization
Janitor uses a list of user email address as administrator user to access its admin endpoint.
The value can be set in `iam.adminUserList` configuration.
### For Broad Engineers:
#### To connect to `tools` Janitor database
```
./cloud_sql_proxy -instances=terra-kernel-k8s:us-central1:crljanitor-db-tools-aa84446ffb6a5fd1=tcp:5432
```
Pull secret from Vault
```
export PGPASSWORD=$(docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read -field='password' secret/dsde/terra/kernel/integration/tools/crl_janitor/postgres/db-creds)
```
Connect to postgres DB
```
psql "host=127.0.0.1 sslmode=disable dbname=crljanitor user=crljanitor"
```

#### To use `tools` Janitor
`tools` Janitor is used by Broad deployed Terra APPs. `tools` Janitor's client service account(created by [Terraform](https://github.com/broadinstitute/terraform-ap-modules/blob/54bf1f9669ade3d4f5e8fb0197f1dd4239448dea/crl-janitor/sa.tf#L76)) has permission to access
admin endpoint. To use this:

Step 1:
```
docker run --rm --cap-add IPC_LOCK -e "VAULT_TOKEN=$(cat ~/.vault-token)" -e "VAULT_ADDR=https://clotho.broadinstitute.org:8200" vault:1.1.0 vault read -format json secret/dsde/terra/kernel/integration/tools/crl_janitor/client-sa | jq -r '.data.key' | base64 --decode > janitor-client-sa.json```
```
Step2: 
```
gcloud auth activate-service-account --key-file=janitor-client-sa.json
```
Step3:
```
gcloud auth print-access-token
```
Go to [tools Janitor Swagger](https://crljanitor.tools.integ.envs.broadinstitute.org/swagger-ui.html) Paste this token into Swagger's `bearerAuth`

#### To use other environment Janitor
The admin user list file is stored in Vault. 
To read user list, run:
```
docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read config/terra/crl-janitor/common/iam 
```
To request admin access, please contact [mc-terra-janitor-admins](https://github.com/orgs/broadinstitute/teams/mc-terra-janitor-admins) members.

To update user list, run:
```
docker run --rm --cap-add IPC_LOCK -e "VAULT_TOKEN=$(cat ~/.vault-token)" -e "VAULT_ADDR=https://clotho.broadinstitute.org:8200" -v $(pwd):/current vault:1.1.0 vault write config/terra/crl-janitor/common/iam admin-users=@/current/{USER_FILE}}
```
