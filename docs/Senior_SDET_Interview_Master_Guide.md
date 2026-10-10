# Senior SDET Infrastructure POC --- Interview Master Guide

> Use this as a speaking guide, not a script to memorize word-for-word.
> Be precise about what was implemented, what was tested, and what
> remains a limitation.

## 1. Your 90-second opening pitch

"I built a local end-to-end quality-engineering POC that demonstrates
how I approach testing beyond UI automation. It contains Spring Boot
Order and Inventory services, PostgreSQL for persistence, and Kafka for
asynchronous order events. I containerized the services and dependencies
with Docker Compose, then deployed them into a local Kubernetes cluster
using kind.

For quality gates, I built Java-based API automation with RestAssured
and TestNG, Playwright UI automation across Chromium, Firefox and
WebKit, and a k6 performance smoke test. I integrated API/UI checks into
GitHub Actions and added a separate k6 workflow. For observability, I
instrumented both Spring Boot services with Micrometer's Prometheus
registry, exposed the Actuator Prometheus endpoint, configured
Prometheus to scrape the services, and connected Grafana to Prometheus.

The goal was to show the full quality loop: deploy, test, measure,
observe failures, and make the setup reproducible. This is a local
single-node POC, so I would not describe it as production-ready. I
intentionally deferred automated CD and focused on reliable deployment
manifests, test automation, CI, and observability."

**Follow-up:** "What did you personally implement?"\
Answer in first person and name the code, manifests, tests and workflows
you actually changed. Do not imply a feature exists unless it is in the
repo and verified.

## 2. Architecture walkthrough (5 minutes)

### Request path

1.  A UI user searches for an order in the frontend.
2.  The UI calls Order Service on port 8080.
3.  Order Service can query PostgreSQL and communicate with Inventory
    Service on port 8081.
4.  The Order Service can publish an order event to Kafka's
    `order-events` topic.
5.  API tests validate status codes, payloads, authentication and
    service integration.
6.  UI tests validate the user flow and error handling; one test mocks a
    backend HTTP 500 to isolate the UI's error state.
7.  k6 generates a small authenticated traffic pattern and measures
    latency, failures and checks.
8.  Prometheus scrapes the Actuator metrics endpoints; Grafana queries
    Prometheus and visualizes selected metrics.
9.  GitHub Actions runs automated checks against the Compose stack.

### Kubernetes explanation

-   **Deployment** declares desired replicas and manages Pods.
-   **Pod** is the smallest schedulable unit and contains the app
    container.
-   **Service** gives stable DNS and a stable virtual IP for a set of
    Pods.
-   **ConfigMap** holds non-secret configuration.
-   **Secret** holds sensitive configuration values (base64 encoding is
    not encryption).
-   **PVC** requests persistent storage for PostgreSQL.
-   **Readiness probe** controls whether a Pod should receive Service
    traffic.
-   **Liveness probe** can cause a container restart if the app is
    considered stuck/unhealthy.
-   **Startup probe** delays liveness/readiness checks until initial
    startup succeeds.
-   **Resource requests** influence scheduling; **limits** cap
    consumption.

Commands to demonstrate:

``` bash
kubectl get pods -n sdet-poc -o wide
kubectl get svc -n sdet-poc
kubectl describe pod <pod-name> -n sdet-poc
kubectl logs <pod-name> -n sdet-poc --previous --tail=100
kubectl rollout status deployment/order-service -n sdet-poc
kubectl rollout history deployment/order-service -n sdet-poc
kubectl rollout undo deployment/order-service -n sdet-poc
```

A `kubectl port-forward` is a temporary local tunnel, not an external
production exposure mechanism.

## 3. Questions and strong answers

### Q1. Why did you create a POC beyond a traditional UI automation framework?

**Answer:** "A Senior SDET needs to reason about failures across the
delivery path, not just locators. The same change can fail at build
time, container startup, service discovery, database connectivity, API
behavior, UI rendering, performance thresholds or observability. This
POC let me exercise those layers and make the tests repeatable through
CI. It also forced me to distinguish a product defect from environment
failures such as image pulls or probe timeouts."

### Q2. Why both Docker Compose and Kubernetes?

**Answer:** "Compose is convenient for local integration and CI: it
brings up the dependency graph with a short command and gives jobs a
disposable environment. Kubernetes demonstrates orchestration concepts
that Compose does not fully model in the same way: Deployments,
Services, ConfigMaps, Secrets, PVCs, probes, resource requests and
rollout/rollback. I use Compose for the automated integration-test stack
and kind for the Kubernetes deployment/operations part of the POC."

**Follow-up:** "Why not use Kubernetes in CI too?"\
"For this time-boxed POC, Compose gave a simpler and faster disposable
test stack. Kubernetes deployment was validated locally with kind. A
future improvement could add a separate kind-based deployment test, but
that adds setup time and resource overhead."

### Q3. Explain the difference between an image, container, Pod, Deployment and Service.

**Answer:** "An image is the packaged filesystem and metadata used to
create a container. A container is a running process created from an
image. A Pod is Kubernetes' scheduling unit and can contain one or more
related containers. A Deployment maintains the desired number of
replicas and manages rollout. A Service provides a stable network
endpoint and selects matching Pods. In my POC, `order-service` is
deployed as a Deployment and exposed inside the cluster by a ClusterIP
Service; Grafana reaches Prometheus through `http://prometheus:9090`
using Kubernetes DNS."

### Q4. Why use kind?

**Answer:** "kind runs Kubernetes nodes as containers, which makes it
practical to test manifests on a laptop without a managed cluster. I
created a cluster named `sdet-poc`, loaded locally built service images
into the kind node, applied the manifests, and validated Pods, Services
and rollouts. The trade-off is that a one-node local cluster doesn't
demonstrate high availability or production networking."

Commands:

``` bash
kind create cluster --name sdet-poc
kind load docker-image <image-name>:latest --name sdet-poc
kubectl get nodes
kubectl get pods -n sdet-poc
```

### Q5. Why did you need `kind load docker-image`?

**Answer:** "The Docker daemon's local image cache and the kind node's
image cache are separate. A local build does not automatically make the
image available inside Kubernetes. I loaded the order and inventory
images into the `sdet-poc` cluster and configured the manifests to use
those local images. Otherwise Kubernetes could try to pull a tag that
only exists locally and end up in `ImagePullBackOff`."

### Q6. Readiness vs liveness vs startup probes?

**Answer:** "Readiness answers whether the Pod should receive traffic;
failing readiness removes it from eligible Service endpoints without
necessarily restarting it. Liveness asks whether the container should be
restarted. Startup probes protect slow-starting apps by giving them a
longer initialization window before liveness checks begin. In this POC,
Grafana's liveness probe returned 503 or timed out during startup,
causing restarts. We increased the timeout and resources and added a
startup probe. The rollout succeeded, but I would still monitor restart
counts to confirm long-term stability."

### Q7. How did you troubleshoot Grafana's repeated restarts?

**Answer:** "I didn't immediately delete the Pod. I first checked
`kubectl get pods`, then used
`kubectl logs deployment/grafana --previous` and `kubectl describe pod`.
Previous logs showed Grafana binding to port 3000, background plugin
installation and SQLite database-lock retries. Kubernetes events
specifically showed liveness/readiness probes returning 503 and timing
out, followed by kubelet killing the container. I patched the Deployment
to raise resource requests/limits, added a startup probe, and increased
probe timeouts from one to five seconds. The rollout succeeded. The
correct next verification is to watch Pod restart counts and events over
time; a successful rollout alone is not proof of permanent resolution."

Commands:

``` bash
kubectl get pods -n sdet-poc
kubectl logs deployment/grafana -n sdet-poc --previous --tail=80
kubectl describe pod <grafana-pod> -n sdet-poc
kubectl rollout status deployment/grafana -n sdet-poc --timeout=240s
```

### Q8. What does a resource request vs limit mean?

**Answer:** "A request is the amount Kubernetes uses for scheduling and
resource accounting; a limit caps the container's resource use. Too-low
CPU can make startup or health endpoints slow; too-low memory can cause
OOM kills. Raising limits also competes with other workloads, especially
on a laptop. In this POC I raised Grafana's memory limit from 256 MiB to
384 MiB and CPU limit from 250m to 500m while adjusting probes. I would
monitor actual resource consumption and restarts before deciding whether
the higher allocation is justified."

### Q9. Why PostgreSQL and Kafka? How are they different?

**Answer:** "PostgreSQL stores durable relational state and supports
queries and transactions. Kafka is a distributed event log for
asynchronous communication; producers write events to topics and
consumers read them independently. An order can be persisted in a
database and an order event can be published for downstream services.
Kafka helps decouple producers from consumers, but introduces eventual
consistency, retries, duplicate delivery considerations,
ordering/partitioning concerns and failure handling."

**Be precise:** The POC's Kafka setup is a single KRaft node with
replication factors of one, appropriate for a local demonstration, not a
resilient production cluster.

### Q10. What is KRaft and why no ZooKeeper?

**Answer:** "KRaft is Kafka's built-in metadata quorum mode, replacing
the older ZooKeeper dependency. The POC runs Kafka in single-node KRaft
mode to reduce components and local resource overhead. A production
deployment would use an appropriate multi-node quorum and replication
strategy."

### Q11. Explain the API automation design.

**Answer:** "The API suite uses Java 21, RestAssured and TestNG. It
covers a successful order lookup, order/inventory integration,
unknown-order 404, event publishing and an unauthenticated 401. The base
URL and credentials are passed through system properties so the same
tests can target local or CI environments. That avoids hardcoding
environment configuration into each test. The suite ran 5/5 during the
POC; I would report the latest workflow result when presenting it."

Run:

``` bash
cd automation/api-tests
mvn --batch-mode test \
  "-DbaseUrl=http://localhost:8080" \
  "-Dapi.username=sdet" \
  "-Dapi.password=sdet123"
```

**Follow-up:** "How would you improve it?"\
"Add schema/contract validation, more boundary and authorization tests,
deterministic test data, correlation IDs, cleanup/isolation, and a
stronger strategy for asynchronous event assertions. Avoid tests
depending on shared mutable records."

### Q12. Why RestAssured and TestNG?

**Answer:** "RestAssured provides a fluent Java DSL for HTTP requests
and response assertions. TestNG provides test lifecycle/configuration,
grouping, parameterization and parallelization capabilities. Java aligns
with the existing SDET skill set and makes it possible to reuse Java
tooling and reporting. I would keep HTTP-client details in helpers where
they improve consistency, but avoid abstraction that hides the actual
assertion."

### Q13. Why test an unauthenticated request?

**Answer:** "A negative security test verifies that the endpoint rejects
an unauthenticated client rather than only proving that a valid client
succeeds. The expected 401 is a contract: it checks that the security
boundary remains enforced. In production, I would add role-based access
tests, expired/invalid credentials, authorization by resource ownership
and checks that error bodies do not leak sensitive details."

### Q14. Explain the UI suite and cross-browser coverage.

**Answer:** "The Playwright suite has four scenarios: search/display an
order, show an unknown-order error, submit using Enter, and handle a
mocked backend HTTP 500. It ran against Chromium, Firefox and WebKit,
giving 12 test/browser combinations. The HTTP 500 scenario uses route
mocking so the UI's error-handling behavior can be tested
deterministically without depending on the real backend to fail at the
right moment."

Run:

``` bash
cd automation/ui-tests
npm ci
npx playwright install --with-deps
npm test
```

**Follow-up:** "Why mock the backend?"\
"To isolate the UI behavior and force a rare failure condition
deterministically. I still need separate integration tests against the
real backend because mocking cannot prove the complete system behaves
correctly."

### Q15. How do you reduce flaky UI tests?

**Answer:** "Use role/label/test-id locators that reflect user intent,
wait for observable states instead of arbitrary sleeps, isolate test
data, avoid ordering dependencies, mock only the failures that need
deterministic control, and collect traces/screenshots on failure.
Cross-browser tests should run against stable application readiness
checks. Retries can help gather evidence but shouldn't be used to hide
real flakiness."

### Q16. Explain the k6 test and its thresholds.

**Answer:** "The smoke test uses two virtual users for 30 seconds and
calls the authenticated order endpoint. It checks HTTP 200 and the
expected order ID. Thresholds are p95 latency below 500 ms, HTTP failure
rate below 1%, and checks above 99%. One run made 50 requests with
average latency around 227 ms, p95 around 447 ms, no HTTP failures and
100% checks. Another run had p95 around 543 ms, which failed the latency
threshold. That variability is why I call this a local smoke test, not a
capacity benchmark. The threshold is a project target, not a universal
production SLO."

Run:

``` bash
k6 run performance/k6/order-api-smoke.js
```

**Follow-up:** "What would a real performance plan include?"\
"Define user journeys and arrival rates from traffic assumptions; ramp
load gradually; separate warm-up from measurement; test baseline, load,
stress, spike and soak profiles; monitor CPU/memory/GC/DB pools/Kafka
lag; use representative data and an isolated environment; establish SLOs
from product requirements; and compare results across repeatable runs."

### Q17. Explain p95 latency.

**Answer:** "p95 is the value below which 95% of observed request
durations fall. It focuses on the slower tail without being dominated by
the single slowest request. It does not mean every request takes that
long, nor does it explain the cause. I correlate it with error rate,
request volume, CPU, memory, database pool wait and dependency latency."

### Q18. How does Prometheus work in this POC?

**Answer:** "The Spring Boot services use Micrometer's Prometheus
registry and expose `/actuator/prometheus`. Prometheus periodically
scrapes those endpoints using Kubernetes Service DNS and Basic Auth
credentials read from mounted Secret files. Prometheus also scrapes
itself. I verified that order-service, inventory-service and Prometheus
targets were UP. Prometheus stores time-series samples and evaluates
PromQL queries; it doesn't automatically make every metric useful
without labels, retention and alert design."

Useful commands and query:

``` bash
curl -i -u 'sdet:sdet123' http://localhost:8080/actuator/prometheus
```

``` promql
up
```

### Q19. Why Grafana as well as Prometheus?

**Answer:** "Prometheus collects and stores time-series metrics and
evaluates PromQL. Grafana queries one or more data sources and presents
dashboards, visualizations and alerting workflows. Grafana is not the
metrics scraper in this setup; it queries Prometheus. In the POC I
configured the data source using `http://prometheus:9090` from inside
Kubernetes, then added panels for request rate, JVM heap, scrape status,
5xx errors and latency. Some panels showed no data because the query had
no matching series or no matching events; I chose not to spend the
time-boxed session tuning every panel."

### Q20. Why can a Grafana panel show "No data"?

**Answer:** "It can mean the metric doesn't exist, the label filter
matches no series, the selected time range contains no samples, or the
expression returns an empty vector. A zero-valued result and no series
are different. For example, a 5xx-rate query can return no series if no
5xx samples exist. I check the raw metric in Prometheus first, verify
labels, set a recent time range, and generate traffic before changing a
query."

### Q21. What is the difference between a 401 and 403?

**Answer:** "401 means authentication is missing or invalid; 403 means
the request is understood but the authenticated principal is not
authorized. Exact behavior can depend on the framework and security
configuration. The POC includes an unauthenticated request expecting
401."

### Q22. How does CI work here?

**Answer:** "GitHub Actions builds the application artifacts, brings up
the Compose stack, waits for the application to become healthy, then
runs API or UI tests and collects logs/artifacts. A separate workflow
runs the k6 smoke test. API/UI workflows passed after we added retries
around Compose startup. Earlier, Docker Hub pulls/login attempts
intermittently timed out. We removed the problematic login step and
added bounded retries around stack startup. A retry is only appropriate
for transient infrastructure errors; it should not mask deterministic
test or build failures."

Example bounded retry:

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

**Follow-up:** "Why not rerun the entire job?"\
"Rerunning can be useful for known transient infrastructure failures,
but can hide flaky tests and wastes time. Prefer retries around the
specific idempotent operation that has transient failures, with bounded
attempts, useful logs and a hard final failure."

### Q23. Why was CD deferred?

**Answer:** "The goal and time budget prioritized proving application
build, test automation, local Kubernetes deployment and observability.
The Kubernetes manifests support manual deployment and rollout/rollback.
I intentionally deferred an automated CD pipeline rather than adding a
pipeline I couldn't validate end-to-end. A follow-up would add a
separate deployment workflow with environment protection, image tagging
by commit SHA, registry push, manifest/chart update, rollout health
verification and rollback behavior."

### Q24. How do you keep secrets safe?

**Answer:** "The `.env` and `k8s/secret.yaml` files are ignored by Git.
Kubernetes Secret data is base64-encoded, not encrypted by that encoding
alone. For this local POC, the Secret supplies application and
Prometheus credentials. Production would require encryption at rest,
restrictive RBAC, external secret management, rotation, least-privilege
credentials, TLS and secret scanning. I verify ignore status and Git
tracking before committing."

Commands:

``` bash
git status --short --ignored .env k8s/secret.yaml
git ls-files .env k8s/secret.yaml
```

The first should show ignored files; the second should not list them.

### Q25. What did you learn from debugging WSL and port-forward issues?

**Answer:** "I separated the layers instead of assuming the application
was down. Windows showed the Ubuntu distro as Running, but an invocation
of `curl.exe` from inside WSL returned a Vsock timeout. Using Linux
`curl` against `127.0.0.1:3000/api/health` returned Grafana HTTP 200;
Prometheus `/-/ready` also returned 200. That proved both services were
reachable at that moment. Separately, `kubectl port-forward` to Grafana
later failed because the target Pod wasn't accepting connections on port
3000, so I inspected Pod state, previous logs and events. The lesson is
to test host connectivity, port-forward, Pod health and application
health separately."

### Q26. What would you improve next?

Prioritize: 1. Confirm Grafana stability and record restarts over time.
2. Make dashboard queries match actual exposed metric names; add
generated test traffic and a deliberately controlled 5xx test if safe.
3. Add service-level alerts and define realistic SLOs. 4. Add API
schema/contract tests and better asynchronous Kafka assertions. 5. Add
test data isolation and cleanup. 6. Add a reproducible kind deployment
smoke test to CI if runner resources allow. 7. Add automated CD with
immutable image tags and rollout verification. 8. Improve secret
management and production hardening. 9. Add performance scenarios beyond
a 30-second smoke test.

Do not claim these follow-ups are implemented until they are.

## 4. Troubleshooting questions: answer with a method

For almost any infrastructure failure, use this sequence:

1.  **Observe:** get current state (`kubectl get`, `docker compose ps`,
    workflow logs).
2.  **Scope:** determine whether it is one container, a dependency,
    network, configuration, or host issue.
3.  **Inspect evidence:** `describe`, current and previous logs, events,
    health endpoint, relevant metrics.
4.  **Form a hypothesis:** cite the exact evidence.
5.  **Make the smallest reversible change.**
6.  **Validate:** rerun health checks, tests and rollout.
7.  **Prevent recurrence:** add a probe, health gate, test, bounded
    retry or better diagnostics only when appropriate.

Example commands:

``` bash
kubectl get pods -n sdet-poc -o wide
kubectl get events -n sdet-poc --sort-by=.lastTimestamp
kubectl describe pod <pod> -n sdet-poc
kubectl logs <pod> -n sdet-poc --tail=100
kubectl logs <pod> -n sdet-poc --previous --tail=100
docker compose ps
docker compose logs --tail=100 <service>
```

## 5. Rapid-fire definitions

-   **Idempotency:** repeating an operation produces the same intended
    state; important for retries and deployment automation.
-   **Eventual consistency:** independent components may temporarily
    observe different states while updates propagate.
-   **Consumer group:** Kafka consumers coordinate partitions so a
    partition is assigned to one consumer in a group at a time.
-   **Partition:** Kafka's ordered log unit; ordering is per partition,
    not globally across a topic.
-   **At-least-once delivery:** events can be delivered more than once;
    consumers should be idempotent or deduplicate.
-   **Backpressure:** a slower downstream component limits the rate
    upstream work is accepted or processed.
-   **SLO:** target reliability/performance objective; **SLI:** measured
    indicator; **SLA:** agreement with consequences.
-   **CI vs CD:** continuous integration validates changes
    automatically; continuous delivery/deployment automates release
    preparation or deployment.
-   **Smoke test:** quick, shallow verification of critical
    functionality.
-   **Regression test:** verifies existing behavior remains correct
    after changes.
-   **Contract test:** validates assumptions at a service boundary,
    often request/response schema or protocol.
-   **Readiness vs liveness:** readiness controls traffic eligibility;
    liveness can trigger restart.
-   **Secret vs ConfigMap:** sensitive values vs non-sensitive
    configuration; Kubernetes Secret encoding is not encryption by
    itself.
-   **Observability:** ability to infer internal system state from
    outputs such as metrics, logs and traces.

## 6. A-grade answer structure

For a strong senior-level answer: 1. State the principle. 2. Explain why
it matters. 3. Tie it to one concrete POC example. 4. Give the command,
query or test that proves it. 5. Explain the trade-off or limitation. 6.
Mention what you would improve next.

Example: "I added retries to the Compose startup operation because the
observed CI failure was a transient image-pull/startup timeout. The
retry is bounded to three attempts with 15-second pauses and a final
nonzero exit, so it improves tolerance without turning a real failure
green. I would not blindly retry the test suite because that can hide
flakiness."

## 7. Final rehearsal checklist

Be ready to explain without notes: - The request path and component
responsibilities. - How a local image gets into kind. - What each
Kubernetes resource does. - Readiness, liveness, startup probes and
resource limits. - The API and UI test scenarios and why they were
chosen. - What the k6 thresholds mean and what the results do **not**
prove. - How Prometheus scrapes metrics and how Grafana queries them. -
The Grafana restart diagnosis and exact evidence. - Why CI retries are
bounded and what CD work was deferred. - Which items are verified and
which are follow-up improvements.

**Golden rule:** never bluff. State what you observed, what the evidence
showed, what change you made, how you verified it, and what remains
unproven.

---

## 8. Practical examples and commands to rehearse

This section closes the gap between a good verbal answer and a hands-on interview answer. Commands are examples for the POC environment; check the actual repo paths and resource names before running them. Never paste real secrets into an interview screen share.

### A. Docker: image, container, logs, networking, and health

**Question: A container exits immediately. What do you do?**

**Answer pattern:** check container state and exit code, inspect logs, inspect configuration, then reproduce with a foreground run if needed. Don't start by deleting the container.

```bash
docker compose ps
docker compose logs --tail=100 order-service
docker inspect <container-name> --format '{{.State.Status}} {{.State.ExitCode}} {{.State.Error}}'
docker compose config
```

**Question: The host can reach an API, but another container cannot. Why?**

Containers should normally communicate over the Compose network using the service name and container port, not `localhost` (which refers to the current container itself).

```bash
docker network ls
docker compose ps
docker compose exec order-service sh
# Inside the container, if curl is installed:
curl -i http://inventory-service:8081/actuator/health
```

If the service image has no shell or curl, use a temporary diagnostic container attached to the Compose network. Explain that `localhost:8081` inside Order Service means Order Service itself, not Inventory Service.

**Question: What is the difference between `EXPOSE` and publishing a port?**

`EXPOSE` documents the container port; it does not itself publish that port on the host. Compose `ports: ["8080:8080"]` publishes host port 8080 to container port 8080. Internal service-to-service traffic usually needs no host-port publication.

```bash
docker compose port order-service 8080
curl -i http://localhost:8080/actuator/health
```

### B. Kubernetes: debug a Pod that is not ready

**Question: A deployment exists, but the application is unavailable. Walk me through diagnosis.**

Use a layered sequence: desired state → Pod state → events → logs → probe/configuration → Service endpoints → network request.

```bash
kubectl get deploy,rs,pods,svc -n sdet-poc -o wide
kubectl describe pod <pod-name> -n sdet-poc
kubectl logs <pod-name> -n sdet-poc --tail=100
kubectl logs <pod-name> -n sdet-poc --previous --tail=100
kubectl get endpointslice -n sdet-poc
kubectl get events -n sdet-poc --sort-by=.lastTimestamp
```

If `ImagePullBackOff`: inspect the image name/tag, registry access, pull secret and events. If `CrashLoopBackOff`: inspect current and previous logs, exit code, configuration and resource/OOM events. If Running but not Ready: inspect readiness-probe path/port, startup time and dependency readiness. If Service has no endpoints: check Pod readiness and Service selector labels.

**Question: How do you deploy a new image and verify rollback?**

```bash
kubectl set image deployment/order-service order-service=<image>:<tag> -n sdet-poc
kubectl rollout status deployment/order-service -n sdet-poc --timeout=180s
kubectl rollout history deployment/order-service -n sdet-poc
kubectl rollout undo deployment/order-service -n sdet-poc
kubectl rollout status deployment/order-service -n sdet-poc --timeout=180s
```

Only use `kubectl set image` if the container name and deployment match the manifest. For the local POC, images built on the host may need loading into kind first:

```bash
kind load docker-image <image>:<tag> --name sdet-poc
kubectl describe pod <pod-name> -n sdet-poc
```

**Question: Why is a Kubernetes Secret not automatically secure?**

Base64 is encoding, not encryption. Restrict access with RBAC, avoid committing literal credentials, enable encryption at rest where supported, rotate credentials, and prefer a managed/external secret solution in production. In this POC, `.env` and `k8s/secret.yaml` were git-ignored; verify they are not tracked:

```bash
git status --short --ignored .env k8s/secret.yaml
git ls-files .env k8s/secret.yaml
```

### C. API automation: concrete RestAssured example

**Question: Show me how you assert a successful response and a negative case.**

A compact illustrative RestAssured example (adapt the endpoint and JSON field to the actual service contract):

```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import org.testng.annotations.Test;

public class OrderApiExampleTest {
    private final String baseUrl = System.getProperty(
        "baseUrl", "http://localhost:8080");

    @Test
    public void existingOrderReturnsExpectedOrder() {
        given()
            .auth().preemptive().basic("sdet", "sdet123")
        .when()
            .get(baseUrl + "/orders/ORD-1001")
        .then()
            .statusCode(200)
            .body("", equalTo("Order ORD-1001 found"));
    }

    @Test
    public void unknownOrderReturnsNotFound() {
        given()
            .auth().preemptive().basic("sdet", "sdet123")
        .when()
            .get(baseUrl + "/orders/DOES-NOT-EXIST")
        .then()
            .statusCode(404);
    }
}
```

Treat this as an interview illustration, **not a guarantee that the exact response body or JSONPath matches your current implementation**. Before presenting it as code from the repository, open the real test and copy the actual assertions. The important points to explain are: arrange the request, execute it, assert status and contract, keep environment configuration outside the test, and make test data deterministic.

Run the real suite:

```bash
cd automation/api-tests
mvn --batch-mode test -DbaseUrl=http://localhost:8080 -Dapi.username=sdet -Dapi.password=sdet123
```

**Follow-up: Why test 401 and 404?** A 401 validates authentication enforcement; a 404 validates missing-resource behavior. They are different contracts. Add assertions for safe error payloads, role-based 403 behavior, invalid inputs, schema, and boundary cases where applicable.

### D. Playwright: user-facing locator and deterministic failure test

**Question: Show a stable UI test and explain why you avoid sleeps.**

Illustrative TypeScript example; adapt accessible names to the actual frontend:

```typescript
import { test, expect } from '@playwright/test';

test('user can search for an order', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('textbox', { name: /order/i }).fill('ORD-1001');
  await page.getByRole('button', { name: /search/i }).click();
  await expect(page.getByText('Order ORD-1001 found')).toBeVisible();
});
```

Playwright's web-first assertions wait for the expected state within a timeout. A fixed `waitForTimeout(3000)` waits blindly: it may be unnecessarily slow on a fast run and still fail on a slow run.

**Question: How do you test an HTTP 500 UI error without depending on a real outage?**

```typescript
import { test, expect } from '@playwright/test';

test('shows a useful error when the API returns 500', async ({ page }) => {
  await page.route('**/orders/**', async route => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Internal server error' })
    });
  });

  await page.goto('/');
  await page.getByRole('textbox', { name: /order/i }).fill('ORD-1001');
  await page.getByRole('button', { name: /search/i }).click();
  await expect(page.getByText(/error|try again|unavailable/i)).toBeVisible();
});
```

This is a pattern, not necessarily the exact selector or error copy in the repository. Mocking tests the UI response deterministically; a separate integration test should cover the real UI-to-service path.

Useful commands:

```bash
cd automation/ui-tests
npm ci
npx playwright install --with-deps
npm test
npx playwright test --project=chromium
npx playwright test --workers=2
```

**Follow-up: What do you capture for a flaky test?** Trace, screenshot, video if configured, browser console, failed network requests, test data, browser/project, retry history, and timestamps. Investigate the first failure rather than treating a passing retry as proof that the test is healthy.

### E. k6: test script, thresholds, and interpretation

**Question: What does a performance threshold do?** It turns a performance objective into a pass/fail gate. The threshold is a chosen target for this environment, not a universal promise about production capacity.

Illustrative k6 pattern:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const failures = new Rate('http_failures');
export const options = {
  vus: 2,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_failures: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const response = http.get('http://localhost:8080/orders/ORD-1001', {
    auth: { username: 'sdet', password: 'sdet123' },
  });
  failures.add(response.status >= 400);
  check(response, {
    'status is 200': r => r.status === 200,
    'expected order is returned': r => String(r.body).includes('ORD-1001'),
  });
  sleep(1);
}
```

Use the repository's actual script for the demo rather than replacing it with this illustration:

```bash
k6 run performance/k6/order-api-smoke.js
```

Explain the POC result accurately: one local run made 50 requests, averaged about 227 ms, p95 was about 447 ms, with 0% HTTP failures and 100% checks. Another run's p95 was about 543 ms and failed the 500 ms threshold. This variability is a reason to call it a smoke test, not a capacity benchmark. Investigate host load, cold starts, sample size, endpoint dependencies and repeatability before drawing conclusions.

### F. Prometheus/Grafana: prove metrics exist before building a panel

**Question: A Grafana panel says “No data.” What do you check?**

1. Confirm the target is `UP` in Prometheus.
2. Search for the metric without label filters.
3. Confirm the selected time range contains samples.
4. Inspect label names/values and only then write a rate/aggregation query.
5. Generate requests if the metric is request-driven; a missing series is not necessarily zero.

Useful commands (run port-forwards in separate terminals if you need local browser access):

```bash
kubectl get pods -n sdet-poc
kubectl get svc -n sdet-poc
kubectl port-forward svc/prometheus 9090:9090 -n sdet-poc
kubectl port-forward svc/grafana 3000:3000 -n sdet-poc
```

PromQL examples to try in Prometheus:

```promql
up
```

```promql
jvm_memory_used_bytes
```

```promql
rate(http_server_requests_seconds_count[5m])
```

The final query only works if the application exposes that exact metric name and the corresponding time series exists. Micrometer naming and available histogram/bucket series depend on configuration. If the query returns no series, inspect the raw `/actuator/prometheus` output before guessing a replacement.

The application endpoint requires authentication in this POC; do not assume an unauthenticated curl is a valid metrics test. Example:

```bash
curl -i -u 'sdet:sdet123' http://localhost:8080/actuator/prometheus
```

### G. GitHub Actions and bounded retries

**Question: Why did you retry Compose startup instead of rerunning all tests?**

A bounded retry around the specific transient operation is narrower and easier to diagnose. A retry must have a limit and must still exit non-zero after final failure. Never convert a persistent failure into a green pipeline.

```bash
for attempt in 1 2 3; do
  if docker compose up -d --build; then
    break
  fi
  if [ "$attempt" -eq 3 ]; then
    docker compose ps
    docker compose logs --no-color --tail=100
    exit 1
  fi
  sleep 15
done
```

A strong explanation of the workflow should cover: checkout, required runtime setup, dependency install/cache where useful, service startup, health/readiness wait, test execution, logs/artifacts on failure, cleanup, and exit status. Avoid claiming that the pipeline deploys to a production cluster: the POC's Kubernetes deployment/rollback was manually validated and automated CD was intentionally deferred.

### H. Linux/networking: isolate the failing layer

**Question: “The endpoint is down.” What do you check?** Be specific about where the command runs: Windows host, WSL, container, or Kubernetes Pod.

```bash
# Is a process listening on a port?
ss -lntp

# Can this environment resolve a hostname?
getent hosts inventory-service

# Is the HTTP endpoint reachable and what status does it return?
curl -i --max-time 5 http://localhost:8080/actuator/health

# Is Kubernetes reporting the expected resources and events?
kubectl get pods,svc -n sdet-poc -o wide
kubectl get events -n sdet-poc --sort-by=.lastTimestamp
```

The POC exposed a useful diagnostic lesson: `curl.exe` from inside WSL produced a Vsock-related timeout, while Linux `curl` against the local forwarded endpoint returned HTTP 200 at that point. Do not conclude that a service is down from one client/path; separate DNS, TCP connection, port-forward, HTTP status, application health, and dependency health.

### I. Senior-level scenario answers: make the trade-off explicit

**Scenario: The tests pass locally but fail in CI.** Compare runtime versions, environment variables, ports, service readiness, secrets, network/DNS assumptions, browser dependencies, timeouts and test data. Preserve logs and reproduce with the same container image. Retry only known transient infrastructure operations; don't blindly retry assertion failures.

**Scenario: A Kafka publish endpoint returns success but the consumer has not acted yet.** Distinguish acceptance/publish acknowledgement from downstream processing. Avoid arbitrary sleeps; poll for a correlated result with a bounded deadline, make the operation idempotent, and define behavior for timeout, duplicate events and retries. Be explicit about what the current POC test proves: an event-publish API check is not automatically proof of exactly-once downstream business processing.

**Scenario: p95 rises but the error rate remains zero.** Check request rate and test mix, CPU throttling, heap/GC, database connection-pool wait, query latency, downstream Inventory latency, Kafka interactions and host contention. Correlate time windows; don't infer a root cause from p95 alone.

**Scenario: How would you make this production-ready?** Do not simply add more replicas. Discuss multi-node availability, external managed database/Kafka, durable storage and backups, resource sizing, autoscaling, TLS and identity, RBAC and secret management, SLOs/alerts, load/soak testing, deployment strategies, rollback, disaster recovery and operational ownership. State that these are improvements, not features demonstrated by this single-node laptop POC.

## 9. How to use this guide before the interview

For every major topic, rehearse the answer in this order:

1. **Concept:** define it in one or two sentences.
2. **POC example:** name the exact service, test, manifest, workflow or failure.
3. **Evidence:** give the command, assertion, query or result that verifies it.
4. **Trade-off:** explain why the approach is reasonable for a time-boxed local POC.
5. **Limitation/next step:** separate implemented behavior from a proposed improvement.

Do not memorize example code as though every snippet is copied from the repo. Several snippets above are illustrative patterns and are labelled as such; inspect the repository before claiming exact file-level implementation. The guide is strongest when you can open the real file and explain it line by line.
