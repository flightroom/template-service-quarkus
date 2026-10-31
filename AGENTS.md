# AGENTS.md - %serviceSlug%

Java 25 LTS + Quarkus 3.40 (Quarkus REST + Jackson) backend service, built with Maven, packaged as a Docker image (JVM mode, fast-jar).

## Layout

- `pom.xml`: Quarkus BOM, extensions `quarkus-rest-jackson`, `quarkus-smallrye-openapi`, `quarkus-smallrye-health`.
- `src/main/java/dev/mfeorchestrator/service/HelloResource.java`: example `GET /api/hello`.
- `src/main/java/dev/mfeorchestrator/service/LivenessCheck.java`, `ReadinessCheck.java`: example `@Liveness` / `@Readiness` health checks.
- `src/main/resources/application.properties`: port, graceful shutdown, OpenAPI info, Swagger UI in prod.
- `src/test/java/...`: `@QuarkusTest` + RestAssured tests.
- `Dockerfile`: multi-stage, non-root user `10001`, healthcheck on `/q/health/live`.
- `docker-entrypoint.sh`: attaches the Application Insights Java agent (`-javaagent` in `JAVA_TOOL_OPTIONS`) only when `APPLICATIONINSIGHTS_CONNECTION_STRING` is set, then runs the JVM.
- `mvnw`, `.mvn/wrapper/`: Maven Wrapper. Always use `./mvnw`, never a system `mvn`.

## Endpoints the platform relies on

- `GET /q/health/live` and `GET /q/health/ready`: Kubernetes probes.
- `GET /q/swagger-ui/` (Swagger UI) and `GET /q/openapi.json` (OpenAPI spec, `/q/openapi` is YAML). Both public, no auth.

## Add an endpoint

Create a resource class with `@Path("/api/...")` in `dev.mfeorchestrator.service` (or a sub-package) and JAX-RS methods (`@GET`, `@POST`, `@Produces(MediaType.APPLICATION_JSON)`). Document it for the OpenAPI spec with `@Tag` on the class and `@Operation(summary = ...)` on each method (`org.eclipse.microprofile.openapi.annotations`); return a record or DTO so the response schema is generated, and add `@APIResponse` for non-200 statuses. Add a `@QuarkusTest` RestAssured test that calls it.

## Add a health check

Add an `@ApplicationScoped` class implementing `org.eclipse.microprofile.health.HealthCheck`, annotated `@Readiness` (gates traffic: database, queue, downstream API) or `@Liveness` (process health only). Return `HealthCheckResponse.up("name")` / `.down("name")`, or `HealthCheckResponse.named("name").status(ok).withData("key", value).build()`. Never check dependencies in a `@Liveness` check: a failing liveness probe restarts the pod. `LivenessCheck` and `ReadinessCheck` are examples: replace their bodies or delete them, the probe endpoints keep working either way.

## Commands

- `./mvnw verify` (tests + fast-jar), `./mvnw quarkus:dev`, `./mvnw test -Dtest=ServiceTest`

## Conventions

- Configuration comes from environment variables only (`${ENV_VAR:default}` in `application.properties`, or `QUARKUS_*` env vars). Never hardcode secrets or URLs.
- Keep the probes, the Swagger UI (`quarkus.swagger-ui.always-include=true`) and `/q/openapi.json` working and unauthenticated. If you add security, permit `/q/health/*`, `/q/swagger-ui/*` and `/q/openapi*`.
- Listen on `PORT` (default 8080). Keep the `quarkus.shutdown.*` graceful shutdown settings.
- Every new endpoint needs a test. Run `./mvnw verify` before finishing.
- Never edit CI files (`.github/`, `.gitlab-ci.yml`, `azure-pipelines.yml`) or Dockerfile base images unless explicitly asked.
- Dependencies are kept current by Dependabot (`.github/dependabot.yml`, weekly, minor/patch grouped). Add new dependencies at their latest stable version; do not pin old versions without a reason.
