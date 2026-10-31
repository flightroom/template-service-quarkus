# %serviceSlug%

Backend service (Java 25 LTS, Quarkus 3.40, Maven) managed by MFE Orchestrator.

## Endpoints

Port `8080` (override with `PORT`). Docs and probes need no auth.

| Purpose      | Method | Path               |
| ------------ | ------ | ------------------ |
| Liveness     | GET    | `/q/health/live`   |
| Readiness    | GET    | `/q/health/ready`  |
| Docs UI      | GET    | `/q/swagger-ui/`   |
| OpenAPI spec | GET    | `/q/openapi.json`  |
| Hello        | GET    | `/api/hello`       |

`/q/swagger-ui` (no trailing slash) redirects (`302`) to `/q/swagger-ui/`. `/q/openapi` serves the same spec as YAML by default (JSON with `Accept: application/json` or `?format=json`). Probes answer `{"status":"UP","checks":[...]}`; readiness answers `503` with `DOWN` during the shutdown delay.

## Run locally

Requires JDK 25. The Maven Wrapper downloads Maven on first use.

```bash
./mvnw quarkus:dev              # http://localhost:8080, Dev UI at /q/dev-ui
curl localhost:8080/q/health/live
open http://localhost:8080/q/swagger-ui/
```

## Configuration

Environment variables only:

| Variable            | Default                           | Description                                                          |
| ------------------- | --------------------------------- | -------------------------------------------------------------------- |
| `PORT`              | `8080`                            | HTTP port                                                            |
| `SHUTDOWN_DELAY`    | `2s`                              | On SIGTERM, readiness reports `DOWN` for this long before shutdown   |
| `SHUTDOWN_TIMEOUT`  | `20s`                             | How long shutdown then waits for in-flight requests                  |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` (image) | JVM flags; the heap follows the container memory limit               |

Any Quarkus property can also be set from the environment (e.g. `QUARKUS_LOG_LEVEL=DEBUG`). Keep `SHUTDOWN_DELAY` + `SHUTDOWN_TIMEOUT` below the Kubernetes `terminationGracePeriodSeconds` (30s by default); on Kubernetes you can raise `SHUTDOWN_DELAY` (e.g. `5s`) so the pod leaves the Service endpoints before connections are closed.

## Application Insights

In Flightroom, attach an "Application Insights" resource to the backend. The platform then passes the container `APPLICATIONINSIGHTS_CONNECTION_STRING`, along with `OTEL_SERVICE_NAME` (the backend slug) and `OTEL_RESOURCE_ATTRIBUTES` (e.g. `deployment.environment=prod,service.version=1.4.2`).

The image ships the [Application Insights Java agent](https://learn.microsoft.com/azure/azure-monitor/app/java-standalone-config) 3.7.10. `docker-entrypoint.sh` adds it to `JAVA_TOOL_OPTIONS` only when `APPLICATIONINSIGHTS_CONNECTION_STRING` is set: without it the JVM starts as before and nothing is sent. With it, requests, outgoing calls, logs and JVM metrics are collected with no code change; the cloud role name is `OTEL_SERVICE_NAME` (copied into `APPLICATIONINSIGHTS_ROLE_NAME` unless that is set) and the application version is `service.version` from `OTEL_RESOURCE_ATTRIBUTES`. The agent logs its own diagnostics to stdout (level: `APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_LEVEL`); `APPLICATIONINSIGHTS_CONFIGURATION_CONTENT` replaces its JSON configuration. Outside the image (`./mvnw quarkus:dev`, `java -jar`) the agent is not attached. A native image cannot load a Java agent: if you build one, use the `quarkus-opentelemetry-exporter-azure` extension instead.

## Test and build

```bash
./mvnw verify                   # tests + target/quarkus-app (fast-jar)
java -jar target/quarkus-app/quarkus-run.jar
```

## Docker

```bash
docker build -t %serviceSlug% .
docker run --rm -p 8080:8080 %serviceSlug%
```

Multi-stage JVM build: `eclipse-temurin:25-jdk-alpine` builds the fast-jar, `eclipse-temurin:25-jre-alpine` runs it as the non-root user `10001`. The `HEALTHCHECK` calls `/q/health/live` with the BusyBox `wget` of the alpine base (no curl in the image).

Native image (optional, not wired in CI): `./mvnw package -Dnative` with GraalVM/Mandrel, or `-Dnative -Dquarkus.native.container-build=true` without a local GraalVM, then a minimal runtime image such as `quay.io/quarkus/quarkus-micro-image:2.0`. It starts in milliseconds and uses less memory, at the cost of much longer builds.

Kubernetes probes:

```yaml
livenessProbe:
  httpGet: { path: /q/health/live, port: 8080 }
readinessProbe:
  httpGet: { path: /q/health/ready, port: 8080 }
```

## Dependency updates

`.github/dependabot.yml` opens weekly pull requests for the Maven dependencies and plugins, the Docker base images and the GitHub Actions, with minor and patch updates grouped into one PR per ecosystem. Majors of the `eclipse-temurin` base images are ignored: move to the next Java LTS on purpose (Dockerfile, CI `java-version`, `maven.compiler.release` in `pom.xml` together). GitLab and Azure DevOps do not read this file: use Renovate there if you need the same.

## Releases

1. Create a new version of the service in the MFE Orchestrator console (this creates a git tag on the repository).
2. The tag triggers CI: tests (`./mvnw -B verify`), image build and push to the container registry, then the image is registered on the console (`POST /api/backends/by-slug/%serviceSlug%/versions/<tag>` with `api-key`).
3. The version shows up in the console with its image reference and digest.

The deploy secret `FLIGHTROOM_API_KEY` is injected in the repository by the console.

- GitHub Actions: `.github/workflows/release.yml` pushes to `ghcr.io/<owner>/<repo>` using `GITHUB_TOKEN`. Optional repo variable `MICROFRONTEND_ORCHESTRATOR_DOMAIN` overrides the console URL.
- GitLab CI: `.gitlab-ci.yml` pushes to the project's container registry.
- Azure DevOps: `azure-pipelines.yml` needs two pipeline variables:
  - `MFE_ORCHESTRATOR_REGISTRY_CONNECTION`: name of the Docker registry service connection;
  - `MFE_ORCHESTRATOR_IMAGE_REPOSITORY`: repository path in the registry (e.g. `my-org/%serviceSlug%`).
  Optionally `MFE_ORCHESTRATOR_REGISTRY_HOST` (e.g. `myregistry.azurecr.io`) so the registered image reference is fully qualified, and `MICROFRONTEND_ORCHESTRATOR_DOMAIN` to override the console URL. The secret comes from the variable group `MFE_ORCHESTRATOR_SECRETS`. The digest is not sent from Azure (optional field).

## Deploys

The orchestrator does not deploy the service: your own deploy pipeline does. So that the console can show which version runs in each environment (domain page, deployed versions matrix), have that pipeline report every successful deploy. Set `ENV_SLUG` (the slug of the environment in the console, e.g. `DEV`, `PROD`), `VERSION`, `IMAGE` and `COMMIT_SHA`, with `FLIGHTROOM_API_KEY` holding the deploy secret:

```bash
DOMAIN="${MICROFRONTEND_ORCHESTRATOR_DOMAIN:-%domain%}"
curl -fsS -X POST "${DOMAIN%/}/api/backends/by-slug/%serviceSlug%/environments/${ENV_SLUG}/deployments" \
  -H "api-key: ${FLIGHTROOM_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"version\":\"${VERSION}\",\"image\":\"${IMAGE}\",\"commitSha\":\"${COMMIT_SHA}\"}"
```

- `ENV_SLUG` is matched regardless of case. When two environments of the project differ only by case (e.g. `prod` and `PROD`), use the exact slug: any other spelling is refused with a 409.
- `version` is required (letters, digits, dots, dashes and underscores, at most 128); `image` (at most 512 characters) and `commitSha` (at most 64) are optional. The version does not have to be one the release CI registered.
- The API key needs the MANAGER role, as the deploy secret has. Every report is kept: the latest one is the version shown for that environment.
