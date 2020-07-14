# terra-resource-janitor
Janitor service to cleanup resources created by [Cloud Resource Library (CRL)](https://github.com/DataBiosphere/terra-cloud-resource-lib) 

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