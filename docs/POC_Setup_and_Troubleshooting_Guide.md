# Senior SDET Infrastructure POC --- Setup & Troubleshooting Guide

> Repository: `senior-sdet-infrastructure-poc`\
> GitHub: https://github.com/rhlpandey1/senior-sdet-infrastructure-poc\
> Purpose: reproducible local infrastructure and test-automation
> demonstration for a Senior SDET interview.

## 1. What this project demonstrates

This POC combines application-level automation with infrastructure, CI,
performance testing, and observability.

-   **Order Service** and **Inventory Service**: Spring Boot services.
-   **PostgreSQL**: relational persistence.
-   **Kafka (KRaft)**: asynchronous order-event messaging without
    ZooKeeper.
-   **Docker / Docker Compose**: containerized local stack.
-   **Kubernetes / kind**: local Kubernetes deployment using manifests.
-   **API automation**: Java 21, RestAssured, TestNG, Allure.
-   **UI automation**: Playwright, Node.js; Chromium, Firefox and
    WebKit.
-   **Performance smoke test**: k6.
-   **CI**: GitHub Actions API/UI workflow and k6 workflow.
-   **Observability**: Spring Boot Actuator + Micrometer Prometheus
    registry, Prometheus and Grafana.

This is a **local single-node POC**, not a production reference
architecture. Secrets, availability, persistence, TLS, authentication,
capacity, and resilience would need further hardening for production.

## 2. Architecture at a glance

``` text
Browser / Playwright
        |
        v
  Order Service :8080  -------> Inventory Service :8081
        |                              |
        +----------+-------------------+
                   |
             PostgreSQL :5432
                   |
              Kafka :9092
           (order-events topic)

Prometheus :9090 --scrapes--> Order Service /actuator/prometheus
                \--scrapes--> Inventory Service /actuator/prometheus

Grafana :3000 ----queries----> Prometheus :9090

GitHub Actions
  ├── API automation job
  ├── UI automation job
  └── k6 performance workflow
```

In Kubernetes, applications communicate through ClusterIP Services and
cluster DNS. `kubectl port-forward` exposes selected services to the
developer's laptop; it does not expose them publicly.

## 3. Repository map

The current repository contains these main areas (verify with
`find . -maxdepth 3 -type f | sort` if files have changed):

``` text
senior-sdet-infrastructure-poc/
├── services/
│   ├── order-service/
│   └── inventory-service/
├── automation/
│   ├── api-tests/
│   └── ui-tests/
├── performance/
│   └── k6/
├── k8s/
│   ├── config.yaml
│   ├── postgres.yaml
│   ├── kafka.yaml
│   ├── order-service.yaml
│   ├── inventory-service.yaml
│   └── observability/
│       ├── prometheus.yaml
│       ├── prometheus-deployment.yaml
│       └── grafana.yaml
├── docker-compose.yml
└── .github/workflows/
    ├── ci.yml
    └── k6-performance.yml
```

Some names or additional files may differ as the repo evolves. Treat
`find` output and the checked-in manifests as the source of truth.

## 4. Environment used for the POC

The working environment was:

-   Windows 11 Home, with WSL 2 and Ubuntu.
-   Docker Engine installed directly inside Ubuntu/WSL (Docker Desktop
    was intentionally avoided to reduce overhead).
-   Docker Compose v5.6.0; Docker Engine 29.8.2.
-   Java 21; Maven 3.9.x.
-   Node.js 22; npm 9.x.
-   Python 3.14.x was available.
-   k6 2.3.0.
-   kubectl 1.37.x and kind 0.30.0.
-   kind cluster `sdet-poc`, namespace `sdet-poc`.

Versions above record the environment used during the POC; use the
versions pinned in repository files where available.

## 5. Fresh-machine setup

### 5.1 Install and validate WSL 2

From **PowerShell as Administrator**, install WSL if it is not already
present:

``` powershell
wsl --install
wsl --list --verbose
```

Install or open Ubuntu from the Windows Start menu. If Windows asks for
a reboot, reboot before continuing. Use Ubuntu for Linux commands unless
a step explicitly says PowerShell.

Check WSL status:

``` powershell
wsl --status
wsl --list --verbose
```

Expected: Ubuntu exists and uses version `2`.

**WSL connection timeout:** In this POC, `wsl -d Ubuntu` once returned
`Wsl/Service/WSAETIMEDOUT`, while `wsl --list --verbose` still showed
Ubuntu as `Running`. First try a simple command:

``` powershell
wsl -d Ubuntu --exec echo WSL_OK
```

Do not immediately run `wsl --shutdown` if Kubernetes or port-forwards
are running; it will stop the WSL environment and interrupt local
services. Save work and stop port-forwards deliberately before any WSL
restart.

### 5.2 Install Docker Engine inside Ubuntu

This POC used Docker Engine inside WSL rather than Docker Desktop.
Installation instructions can change by Ubuntu release; follow the
official Docker Engine for Ubuntu guide for the supported apt repository
setup. Then validate:

``` bash
docker --version
docker compose version
docker buildx version
sudo docker run --rm hello-world
```

If Docker is intended to work without `sudo`, add the user to the Docker
group and start a new shell/session:

``` bash
sudo usermod -aG docker "$USER"
```

Log out of Ubuntu and reopen it, then verify:

``` bash
docker ps
```

**Permission denied on `/var/run/docker.sock`:** the group change does
not apply to an already-running shell. Reopen the shell or run
`newgrp docker`, then retry. Docker-group membership grants
root-equivalent access to the machine; use it only on a trusted
development environment.

**Docker command not found:** confirm Docker Engine is installed inside
the Ubuntu distro where you are running commands. Installing a Windows
Docker client does not necessarily install a Linux daemon in WSL.

### 5.3 Install Java, Maven and Node

Validate:

``` bash
java -version
mvn -version
node --version
npm --version
```

Use Java 21 for this project. Install Java 21, Maven, and Node.js 22 if
absent, following their official installation instructions. Ensure
`JAVA_HOME` and `PATH` point to the intended JDK. Do not switch to a
newer Java version unless the project's toolchain and dependencies have
been validated.

### 5.4 Install kind and kubectl

Install `kubectl` and `kind` using their official instructions, then
verify:

``` bash
kubectl version --client
kind version
docker info
```

Create the local cluster:

``` bash
kind create cluster --name sdet-poc
kubectl cluster-info
kubectl get nodes
```

Use the cluster name consistently. If it already exists, inspect it
instead of creating a second cluster:

``` bash
kind get clusters
kubectl config current-context
```

Expected context is usually `kind-sdet-poc`.

### 5.5 Clone the repository

``` bash
git clone https://github.com/rhlpandey1/senior-sdet-infrastructure-poc.git
cd senior-sdet-infrastructure-poc
git status
```

If using a fork, replace the URL with your own repository.

## 6. Configure local secrets safely

The project uses an `.env` file for local Compose configuration and a
Kubernetes Secret named `app-secrets` for Kubernetes workloads. **Never
commit real `.env` files or `k8s/secret.yaml`.**

Inspect expected variable names without printing secret values:

``` bash
grep -n '^[A-Za-z_][A-Za-z0-9_]*=' .env.example 2>/dev/null
grep -n '^[A-Za-z_][A-Za-z0-9_]*=' .env 2>/dev/null | cut -d= -f1
```

If a sample file exists, copy it and fill in local-only disposable
values:

``` bash
cp .env.example .env
```

If no sample exists, inspect `docker-compose.yml` and manifests to
determine required variable names before creating `.env`; do not guess
credentials. Keep credentials consistent between local services and
automation.

Check ignore status:

``` bash
git status --short --ignored .env k8s/secret.yaml
```

Expected for ignored files: lines beginning with `!!`. If a secret file
was already tracked, `.gitignore` alone does not untrack it; remove it
from Git's index and rotate any exposed credential.

## 7. Run with Docker Compose

First inspect the Compose configuration:

``` bash
docker compose config --quiet
docker compose config --services
```

Build and start the stack:

``` bash
docker compose up -d --build
docker compose ps
```

Follow logs for a specific service:

``` bash
docker compose logs --tail=100 order-service
docker compose logs --tail=100 inventory-service
docker compose logs --tail=100 postgres
docker compose logs --tail=100 kafka
```

Check health endpoints (local credentials are `sdet` / `sdet123` in the
POC; use only disposable development credentials):

``` bash
curl -i http://localhost:8080/actuator/health
curl -i -u 'sdet:sdet123' http://localhost:8080/actuator/prometheus
curl -i http://localhost:8081/actuator/health
```

Test the order endpoint:

``` bash
curl -i -u 'sdet:sdet123' http://localhost:8080/orders/ORD-1001
curl -i -u 'sdet:sdet123' http://localhost:8080/orders/ORD-1001/inventory
```

The exact response depends on the current sample data and service
implementation. Use the endpoint definitions in the current source as
the source of truth.

Stop Compose when done:

``` bash
docker compose down
```

Only remove volumes if you intentionally want to delete local persisted
data:

``` bash
docker compose down -v
```

Do not run `docker compose down` while actively using the same local
stack for testing.

## 8. Deploy to Kubernetes with kind

### 8.1 Build application images

Build the service images using the repo's Dockerfiles/build
instructions. A typical Maven build for each service is:

``` bash
mvn -B -DskipTests package
```

Run it from the relevant service directory. Then build images using the
image names referenced by the manifests. In this POC those names were:

``` text
senior-sdet-infrastructure-poc-order-service:latest
senior-sdet-infrastructure-poc-inventory-service:latest
```

Use the actual image names in the current manifests if they differ.

### 8.2 Load images into kind

kind nodes have their own image cache. Building an image in Docker does
not automatically make it available inside kind.

``` bash
kind load docker-image \
  senior-sdet-infrastructure-poc-order-service:latest \
  senior-sdet-infrastructure-poc-inventory-service:latest \
  --name sdet-poc
```

If the manifest uses a different tag, load that exact tag. For a
local-only image, use an appropriate `imagePullPolicy` (commonly
`IfNotPresent` or `Never`) so Kubernetes does not try to fetch it from a
registry.

### 8.3 Create namespace and Kubernetes Secret

``` bash
kubectl create namespace sdet-poc --dry-run=client -o yaml | kubectl apply -f -
```

Create the `app-secrets` secret from local environment values using the
variable names required by the manifests. Example shape (substitute
values locally; do not paste secrets into chat or commit them):

``` bash
kubectl create secret generic app-secrets -n sdet-poc \
  --from-literal=DB_USERNAME='YOUR_DB_USERNAME' \
  --from-literal=DB_PASSWORD='YOUR_DB_PASSWORD' \
  --from-literal=SECURITY_USERNAME='YOUR_APP_USERNAME' \
  --from-literal=SECURITY_PASSWORD='YOUR_APP_PASSWORD' \
  --dry-run=client -o yaml | kubectl apply -f -
```

If your checked-in manifest expects different key names, follow the
manifest. This command replaces the secret declaratively and is safe to
rerun with the same values.

### 8.4 Apply manifests in dependency order

Inspect available manifests first:

``` bash
find k8s -maxdepth 3 -type f -name '*.yaml' -print
```

Apply namespace/config/secret, then PostgreSQL and Kafka, then Inventory
and Order services. For example, adjust paths to match actual files:

``` bash
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/kafka.yaml
kubectl apply -f k8s/inventory-service.yaml
kubectl apply -f k8s/order-service.yaml
```

Apply observability after application services exist:

``` bash
kubectl apply -f k8s/observability/prometheus.yaml
kubectl apply -f k8s/observability/prometheus-deployment.yaml
kubectl apply -f k8s/observability/grafana.yaml
```

Validate:

``` bash
kubectl get all -n sdet-poc
kubectl get pods -n sdet-poc -o wide
kubectl get svc -n sdet-poc
kubectl get pvc -n sdet-poc
kubectl rollout status deployment/order-service -n sdet-poc --timeout=180s
kubectl rollout status deployment/inventory-service -n sdet-poc --timeout=180s
kubectl rollout status deployment/prometheus -n sdet-poc --timeout=180s
kubectl rollout status deployment/grafana -n sdet-poc --timeout=240s
```

Names are case-sensitive. Use `kubectl get deployments -n sdet-poc` to
verify exact deployment names before running rollout commands.

### 8.5 Access local services using port-forward

Open a terminal for each port-forward you need, or run them sequentially
in separate tabs:

``` bash
kubectl port-forward service/order-service 8080:8080 -n sdet-poc
kubectl port-forward service/prometheus 9090:9090 -n sdet-poc
kubectl port-forward service/grafana 3000:3000 -n sdet-poc
```

Leave each command running in its terminal. Use `Ctrl+C` to stop a
port-forward. Stopping a port-forward does **not** stop the Kubernetes
Service or Pod. Prometheus scrapes the services using internal
Kubernetes DNS (`order-service:8080` and `inventory-service:8081`), not
the laptop's forwarded ports.

Useful URLs:

-   Order Service health: `http://localhost:8080/actuator/health`
-   Prometheus: `http://localhost:9090`
-   Grafana: `http://localhost:3000`

Grafana login used in this POC: username `admin`; password is the
Kubernetes secret's `SECURITY_PASSWORD` value. Retrieve locally without
sharing it:

``` bash
kubectl get secret app-secrets -n sdet-poc \
  -o jsonpath='{.data.SECURITY_PASSWORD}' | base64 --decode; echo
```

## 9. API automation

Framework: Java 21, RestAssured 5.5.6, TestNG 7.11, Allure 2.29.1. The
API test configuration supports system properties:

-   `baseUrl` (default `http://localhost:8080`)
-   `api.username` (default `sdet`)
-   `api.password` (default `sdet123`)

Run from `automation/api-tests`:

``` bash
mvn --batch-mode test
```

For an explicit target and credentials:

``` bash
mvn --batch-mode test \
  "-DbaseUrl=http://localhost:8080" \
  "-Dapi.username=sdet" \
  "-Dapi.password=sdet123"
```

The known suite contained five tests: order lookup, order/inventory
integration, unknown order 404, Kafka event publishing, and
unauthenticated 401. The 5/5 result was observed during the POC; always
rerun and record current results.

If the app runs in Kubernetes, start the order-service port-forward
first. If it runs in Compose, ensure Compose is healthy and do not have
conflicting processes binding port 8080.

## 10. UI automation

Framework: Playwright with Node.js 22. Four tests were run against
Chromium, Firefox and WebKit, resulting in 12 browser/test combinations
passing during the POC.

From `automation/ui-tests`:

``` bash
npm ci
npx playwright install --with-deps
npm test
```

The project's test script uses two workers. Tests cover order
search/display, unknown-order error, Enter-key submission, and a mocked
backend HTTP 500 response.

If `npm ci` fails, check that `package-lock.json` is present and
synchronized with `package.json`. If browsers are missing, run the
Playwright browser install command. Make sure the application base URL
and credentials match the environment.

## 11. k6 performance smoke test

The test file is `performance/k6/order-api-smoke.js`. It sends
authenticated requests to `GET /orders/ORD-1001`, checks status and
order ID, and sleeps one second between iterations.

Configured thresholds:

-   `http_req_duration`: p95 \< 500 ms.
-   `http_req_failed`: rate \< 1%.
-   `checks`: rate \> 99%.
-   Two virtual users for 30 seconds.

Run against the locally accessible Order Service:

``` bash
k6 run performance/k6/order-api-smoke.js
```

If the app is not at `http://localhost:8080`, pass a different base URL:

``` bash
BASE_URL=http://localhost:8080 k6 run performance/k6/order-api-smoke.js
```

During the POC, one successful run produced 50 requests, average 227.32
ms, median 201.27 ms, p90 330.49 ms, p95 447.13 ms, maximum 507.99 ms,
0% HTTP failures and 100% checks. A prior run exceeded the p95 threshold
at about 543 ms. This illustrates local-machine noise and why a smoke
test is not a production load test. Do not claim the service has a
universal latency guarantee based on this run.

## 12. Prometheus and Grafana

### 12.1 Application instrumentation

Both Spring Boot services include `micrometer-registry-prometheus`.
Their `application.properties` exposes health and Prometheus metrics:

``` properties
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.probes.enabled=true
management.prometheus.metrics.export.enabled=true
```

The Prometheus endpoint requires HTTP Basic authentication in this POC.
The Prometheus configuration reads username/password from mounted
Kubernetes Secret files. Do not remove authentication just to make
scraping work.

Validate the Order Service endpoint:

``` bash
curl -i http://localhost:8080/actuator/health
curl -i -u 'sdet:sdet123' http://localhost:8080/actuator/prometheus
```

Use actual local credentials if they differ.

### 12.2 Prometheus targets

Open `http://localhost:9090/targets`. During the POC, `order-service`,
`inventory-service`, and `prometheus` targets were all `UP`.

Or query:

``` promql
up
```

Useful Grafana queries that returned data in the POC included:

``` promql
sum(rate(http_server_requests_seconds_count[5m]))
```

``` promql
sum(jvm_memory_used_bytes{area="heap"})
```

``` promql
up{job=~"order-service|inventory-service"}
```

The error-rate query can return no series if no 5xx responses have
occurred:

``` promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
```

A latency query using histogram buckets will only work if the bucket
metric exists. Verify in Prometheus before assuming a query is valid:

``` promql
{__name__=~"http_server_requests.*"}
```

No data does not always mean a broken dashboard: it may mean no matching
series, no requests in the selected time window, or a metric name that
differs from the query.

### 12.3 Grafana instability encountered

The Grafana pod repeatedly restarted because Kubernetes
liveness/readiness probes received HTTP 503 or timed out. Previous logs
showed Grafana listening on port 3000, SQLite "database is locked"
retries, plugin installation, and then a termination signal. Events
confirmed liveness failures triggered container restarts.

The deployment was patched to: - Increase resource requests to CPU
`100m`, memory `192Mi`. - Increase limits to CPU `500m`, memory
`384Mi`. - Add a startup probe with a 10-second period, 5-second timeout
and failure threshold 18. - Increase liveness/readiness probe timeout to
5 seconds.

A rollout completed successfully after this change. **This proves the
rollout succeeded, not that the issue is permanently resolved.**
Validate stability with:

``` bash
kubectl get pods -n sdet-poc
kubectl describe pod -l app=grafana -n sdet-poc
kubectl logs deployment/grafana -n sdet-poc --tail=100
```

If restarts continue, inspect `Last State`, `Reason`, `Exit Code`,
events and memory pressure before making further changes. The laptop has
8 GB RAM and WSL was configured with roughly 3.7 GiB available, so
increasing memory limits has a real trade-off.

## 13. CI workflows

The repository contains GitHub Actions workflows for API/UI CI and k6
performance smoke testing. The API and UI jobs were observed passing
during the POC. Docker Hub pulls intermittently timed out; retry logic
was added around `docker compose up -d --build`, and the Docker Hub
login/diagnostic steps were removed after login attempts timed out
despite curl endpoints responding.

The startup retry loop used:

``` bash
for attempt in 1 2 3; do
  if docker compose up -d --build; then
    echo "Application stack started successfully"
    exit 0
  fi
  echo "Compose startup attempt $attempt failed"
  if [ "$attempt" -lt 3 ]; then sleep 15; fi
done
echo "Application stack failed to start after 3 attempts"
exit 1
```

A retry helps with transient infrastructure failures; it should not hide
a deterministic build/test failure. Always inspect logs and final exit
status.

Check CI in GitHub Actions rather than assuming the latest commit
passed. Automated CD was intentionally deferred in this POC; deployment
was demonstrated using Kubernetes manifests and manual `kubectl apply`.

## 14. Troubleshooting cookbook

### Pod stuck in Pending / ImagePullBackOff

``` bash
kubectl get pods -n sdet-poc
kubectl describe pod <pod-name> -n sdet-poc
kubectl get events -n sdet-poc --sort-by=.lastTimestamp
```

Check image name/tag, whether image was loaded into kind, image pull
policy, resource availability and Secret/config references.

### Container CrashLoopBackOff

``` bash
kubectl logs <pod-name> -n sdet-poc --previous --tail=100
kubectl describe pod <pod-name> -n sdet-poc
```

Use `--previous` to see the last container instance. Diagnose before
deleting pods or restarting the cluster.

### Service/port-forward connection refused

``` bash
kubectl get pods,svc -n sdet-poc
kubectl get endpoints -n sdet-poc
kubectl port-forward service/grafana 3000:3000 -n sdet-poc
```

A message like
`failed to connect to localhost:3000 inside namespace ... connection refused`
means the process was not accepting connections inside the target pod at
that moment. Inspect pod readiness, logs and restarts. Re-establish the
port-forward only after the pod is healthy.

### Grafana `NetworkError when attempting to fetch resource`

First test Grafana and Prometheus locally:

``` bash
curl -i --max-time 5 http://127.0.0.1:3000/api/health
curl -i --max-time 5 http://127.0.0.1:9090/-/ready
```

If both return HTTP 200, check the browser's failing Network request and
Grafana data-source settings. For Grafana-to-Prometheus inside
Kubernetes, use `http://prometheus:9090`, not `localhost:9090`. A
successful Prometheus data-source "Save & test" confirms the connection.

### Prometheus target is DOWN

``` bash
kubectl logs deployment/prometheus -n sdet-poc --tail=100
kubectl get svc,endpoints -n sdet-poc
```

Verify target service names/ports, endpoint exposure, Basic Auth
username/password file paths, and application metrics endpoint. Test
`/actuator/prometheus` with authentication locally.

### Metrics panel shows No data

-   Set Grafana time range to **Last 15 minutes**.
-   Generate traffic by running API tests or making authenticated
    requests.
-   Confirm metric exists in Prometheus before editing the query.
-   A 5xx-rate query may return no series when there are no errors.
-   Histogram quantile requires the appropriate `_bucket` metric.

### Docker Hub / Compose startup timeout in CI

-   Read the first actual Compose error; do not rely only on final
    workflow failure.
-   Retry only transient pulls/startup, not application test failures.
-   Ensure the Compose stack has a health check and the workflow waits
    for health before running tests.
-   Avoid exposing registry tokens in logs. The POC removed the Docker
    Hub login step after its connection timed out.

### WSL `WSAETIMEDOUT` or `UtilAcceptVsock ... accept4 failed 110`

Check from PowerShell:

``` powershell
wsl --list --verbose
wsl -d Ubuntu --exec echo WSL_OK
```

If Ubuntu is shown as Running but commands time out, WSL communication
may be unhealthy. A `curl.exe` invocation from inside Ubuntu calls the
Windows executable; use Linux `curl` for a Linux-side test. Avoid
`wsl --shutdown` until ready to interrupt Docker, kind, and
port-forwards.

### `kubectl top pods` says metrics API unavailable

Metrics Server was not installed in this POC. Do not install it just to
run the k6 smoke test. Use `docker stats`, pod events, logs and resource
requests/limits for the current scope. Installing Metrics Server is
optional follow-up work.

## 15. Safe final verification checklist

``` bash
git status --short
git status --short --ignored .env k8s/secret.yaml
kubectl get pods -n sdet-poc
kubectl get svc -n sdet-poc
kubectl get pods -n sdet-poc -o wide
```

Then: 1. Confirm API/UI workflows in GitHub Actions. 2. Run API and UI
tests against a healthy stack. 3. Run k6 only if sufficient resources
are available. 4. Confirm Prometheus targets are `UP`. 5. Confirm
Grafana remains stable for several minutes. 6. Ensure no secret files
are tracked. 7. Commit and push the documentation.

## 16. Cleanup

Stop port-forwards with `Ctrl+C` in their terminals.

To remove only the POC Kubernetes namespace:

``` bash
kubectl delete namespace sdet-poc
```

This deletes the workloads and namespaced resources in that namespace,
including the Kubernetes Secret and Grafana's in-container data. Use
only when you intend to tear down the POC. To delete the whole kind
cluster:

``` bash
kind delete cluster --name sdet-poc
```

For Compose:

``` bash
docker compose down
```

Use `docker compose down -v` only when you intentionally want to remove
persistent Compose volumes.

## 17. Known limitations and honest interview framing

-   One-node kind cluster; not HA or representative of production
    scheduling.
-   Kafka runs as one KRaft node with replication factors of one for
    this POC.
-   PostgreSQL persistence and backups are demo-level, not production
    disaster recovery.
-   k6 is a short smoke test, not capacity or endurance testing.
-   Grafana initially restarted due to health-probe failures during slow
    initialization; resources and probes were adjusted and rollout
    succeeded, but stability should be verified.
-   Some dashboard panels may show no data if there are no matching time
    series or no 5xx events.
-   CD automation was intentionally deferred.
-   No Metrics Server was installed.
-   Development credentials are for local demonstration only; production
    requires proper secret management, rotation, TLS, RBAC and least
    privilege.

These limitations are not weaknesses if explained clearly. A senior SDET
should distinguish a validated local POC from a production-ready
platform.
