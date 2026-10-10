# Senior SDET Infrastructure POC

An end-to-end Senior SDET demonstration covering service integration,
API/UI automation, performance smoke testing, Kubernetes deployment, CI
and metrics observability.

## What is included

-   **Services:** Spring Boot Order Service and Inventory Service
-   **Data and messaging:** PostgreSQL and Kafka in KRaft mode
-   **Containers:** Docker and Docker Compose
-   **Kubernetes:** kind cluster, Deployments, Services, ConfigMaps,
    Secrets, PVCs, health probes and resource controls
-   **API automation:** Java 21, RestAssured, TestNG and Allure
-   **UI automation:** Playwright across Chromium, Firefox and WebKit
-   **Performance:** k6 smoke test with latency, failure-rate and check
    thresholds
-   **CI:** GitHub Actions workflows for API/UI and k6
-   **Observability:** Spring Boot Actuator, Micrometer Prometheus
    registry, Prometheus and Grafana

## Architecture

``` text
UI / Playwright
      |
      v
Order Service :8080 ------> Inventory Service :8081
      |                              |
      +-----------> PostgreSQL <-----+
      |
      +-----------> Kafka (order-events)

Prometheus scrapes Order and Inventory Actuator metrics.
Grafana queries Prometheus and displays dashboards.
GitHub Actions runs API/UI tests and the k6 smoke workflow.
```

## Repository structure

``` text
services/                 Spring Boot services
automation/api-tests/     Java + RestAssured + TestNG API tests
automation/ui-tests/      Playwright UI tests
performance/k6/           k6 performance smoke test
k8s/                      Kubernetes manifests
k8s/observability/         Prometheus and Grafana manifests
.github/workflows/         CI workflows
```

Use `find . -maxdepth 4 -type f | sort` to inspect the current exact
file layout.

## Quick start

### Prerequisites

-   Windows with WSL 2 + Ubuntu, or a compatible Linux environment
-   Docker Engine and Docker Compose
-   Java 21 and Maven
-   Node.js 22 and npm
-   `kubectl` and `kind` for the Kubernetes path
-   `k6` for the performance test

### Clone and validate

``` bash
git clone https://github.com/rhlpandey1/senior-sdet-infrastructure-poc.git
cd senior-sdet-infrastructure-poc
git status
docker --version
docker compose version
java -version
mvn -version
node --version
kubectl version --client
kind version
```

### Run with Docker Compose

Inspect the Compose config and required environment variables first:

``` bash
docker compose config --quiet
docker compose config --services
```

Create a local `.env` using the repo's example file if present, then
start the stack:

``` bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

If this repository version has no `.env.example`, inspect
`docker-compose.yml` before creating `.env`; do not guess variable
names. Use disposable local credentials only.

Check application health and order lookup (the POC's local default
credentials were `sdet` / `sdet123`; use the current configured values):

``` bash
curl -i http://localhost:8080/actuator/health
curl -i -u 'sdet:sdet123' http://localhost:8080/orders/ORD-1001
```

### Run API tests

``` bash
cd automation/api-tests
mvn --batch-mode test \
  "-DbaseUrl=http://localhost:8080" \
  "-Dapi.username=sdet" \
  "-Dapi.password=sdet123"
```

### Run UI tests

``` bash
cd automation/ui-tests
npm ci
npx playwright install --with-deps
npm test
```

The observed POC run passed four scenarios across three browser engines
(12 browser/test combinations).

### Run the k6 smoke test

From the repository root, with the Order Service available on port 8080:

``` bash
k6 run performance/k6/order-api-smoke.js
```

The test uses two virtual users for 30 seconds and thresholds of p95
latency below 500 ms, HTTP failure rate below 1%, and checks above 99%.
Results depend on the machine and runtime conditions; this is a smoke
test, not a capacity benchmark.

## Kubernetes and observability

The local kind cluster used the name `sdet-poc` and namespace
`sdet-poc`. Apply manifests in dependency order after creating the
namespace and Kubernetes Secret:

``` bash
kind create cluster --name sdet-poc
kubectl create namespace sdet-poc
kubectl get nodes
find k8s -maxdepth 3 -type f -name '*.yaml' -print
```

Read the exact manifests and variable names before applying them. Load
locally built application images into kind with
`kind load docker-image ... --name sdet-poc` when using local-only image
tags.

Useful checks:

``` bash
kubectl get pods -n sdet-poc -o wide
kubectl get svc -n sdet-poc
kubectl get events -n sdet-poc --sort-by=.lastTimestamp
```

Port-forward only the services you need:

``` bash
kubectl port-forward service/order-service 8080:8080 -n sdet-poc
kubectl port-forward service/prometheus 9090:9090 -n sdet-poc
kubectl port-forward service/grafana 3000:3000 -n sdet-poc
```

URLs: Order Service `http://localhost:8080`, Prometheus
`http://localhost:9090`, Grafana `http://localhost:3000`. Grafana uses
Prometheus's in-cluster URL `http://prometheus:9090` as its data source.

## Detailed documentation

-   **[Setup and Troubleshooting
    Guide](docs/POC_Setup_and_Troubleshooting_Guide.md)** ---
    fresh-machine setup, environment, Compose, Kubernetes, API/UI/k6
    commands, observability, known failures and recovery steps.
-   **[Senior SDET Interview Master
    Guide](docs/Senior_SDET_Interview_Master_Guide.md)** ---
    architecture pitch, design decisions, concrete POC examples,
    commands, common interview questions and follow-up improvements.

Copy the two generated guides into `docs/` in this repository if they
are not there yet.

## Security notes

-   Never commit `.env`, `k8s/secret.yaml`, tokens, or real credentials.
-   Kubernetes Secret values are base64-encoded; base64 is not
    encryption.
-   The default credentials mentioned here are for local demonstration
    only. Use proper secret management, rotation, TLS and least
    privilege outside a disposable local environment.
-   Check `git status --short --ignored .env k8s/secret.yaml` before
    committing.

## Validation status and limitations

During the POC, the API suite passed 5/5 tests, the Playwright suite
passed 12/12 browser/test combinations, the API/UI GitHub Actions
workflows passed after bounded Compose-start retries were added, and
Prometheus showed the Order Service, Inventory Service and Prometheus
targets as `UP`. A k6 run met the configured thresholds, though another
run exceeded the p95 threshold; local latency varied.

Grafana experienced repeated liveness/readiness failures during plugin
initialization. Resource limits and probes were adjusted and a rollout
succeeded; confirm stability before treating the issue as fully
resolved.

This is a local single-node POC. Kafka replication is one, k6 is a short
smoke test, production secret management and high availability are out
of scope, and automated CD was intentionally deferred.
