# crl-janitor
Janitor service to cleanup resources created by [Cloud Resource Library (CRL)](https://github.com/DataBiosphere/terra-cloud-resource-lib) 

# Development

## Using the Gradle wrapper
Set executable permissions:
```
chmod +x gradlew
```
Start local server
```
./gradlew bootRun
```
And then check http://127.0.0.1:8080/status for service status.

Swagger in local server: http://127.0.0.1:8080/swagger-ui.html