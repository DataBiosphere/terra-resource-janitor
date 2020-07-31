# terra-resource-janitor
Janitor service to cleanup resources created by [Cloud Resource Library (CRL)](https://github.com/DataBiosphere/terra-cloud-resource-lib) 

## Overview
TODO

## Primay & Secondary Instances
The Janitor is expected to be deployed with one primary instance and 0-many secondary instances.
The primary instance controls the lifecycle of tracked resources. Having a single primary instance
live at a given time makes it easier to reason about concurrency. Only actions that should not be
done by multiple instances should be confined to the primary.

# Development

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

You can connect to local postgres by running: 
```
psql postgresql://127.0.0.1:5432/testdb -U janitor-test
```

## Deploy to GKE cluster:
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure Skaffold is installed on your local machine 
(available at https://skaffold.dev/). 

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
local-dev/setup_gke_deploy.sh <environment>
```

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

Note that you must stop the local postgres first to free the 5432 port.
See [this document](https://cloud.google.com/sql/docs/postgres/connect-admin-proxy) for more details.

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

## 