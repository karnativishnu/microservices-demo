# microservices-demo

A minimal 3-service Spring Boot app, built from scratch, for practicing
Docker, Kubernetes, Helm, and monitoring — independent of the banking API
project in this repo.

## Services

| Service | Port | Responsibility |
|---|---|---|
| `inventory-service` | 8081 | Owns product/stock data (H2 in-memory DB) |
| `order-service` | 8082 | Places orders; calls `inventory-service` to check & reduce stock |
| `api-gateway` | 8080 (8083 on the Docker host) | Single entry point; routes `/api/inventory/**` and `/api/orders/**` |

```
Client -> api-gateway (8083) -> order-service (8082) -> inventory-service (8081)
                              -> inventory-service (8081) directly too
```

## Run locally without Docker

Each service is a standalone Maven project.

```bash
# terminal 1
cd inventory-service && ./mvnw spring-boot:run   # or: mvn spring-boot:run

# terminal 2
cd order-service && mvn spring-boot:run

# terminal 3
cd api-gateway && mvn spring-boot:run
```

## Run with Docker Compose

```bash
cd microservices-demo
docker compose up --build
```

## Try it out

```bash
# list seeded products (via gateway)
curl http://localhost:8083/api/inventory/products

# place an order for 2 units of product id 1 (Laptop)
curl -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'

# check stock was reduced
curl http://localhost:8083/api/inventory/products/1

# list orders
curl http://localhost:8083/api/orders
```

## Run with Kubernetes (alternative to Compose)

Kubernetes does not build images, so build them first and load them into your
local cluster.

```bash
# build one image per service
docker build -t inventory-service:1.0.0 ./inventory-service
docker build -t order-service:1.0.0 ./order-service
docker build -t api-gateway:1.0.0 ./api-gateway

# make them visible to the cluster (minikube)
minikube image load inventory-service:1.0.0
minikube image load order-service:1.0.0
minikube image load api-gateway:1.0.0
# kind equivalent: kind load docker-image inventory-service:1.0.0

kubectl apply -R -f k8s/
kubectl get pods -n microservices-demo -w
```

Layout of `k8s/`:

```
k8s/
  00-namespace.yaml                        # namespace: microservices-demo
  01-configmap.yaml                        # INVENTORY_SERVICE_URL / ORDER_SERVICE_URL
  deployments/                             # one Deployment per service (pods, probes, image)
    inventory-service-deployment.yaml
    order-service-deployment.yaml
    api-gateway-deployment.yaml
  services/                                # one Service per deployment (DNS name + ClusterIP)
    inventory-service-service.yaml
    order-service-service.yaml
    api-gateway-service.yaml               # NodePort 30080, the only externally reachable one
```

A Deployment and its Service are linked purely by labels: the Service's
`spec.selector` matches the pod template's `metadata.labels`.

Apply just one layer while practising:

```bash
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/services/
```

Reach the gateway:

```bash
minikube service api-gateway -n microservices-demo --url
# or port-forward, which works on any cluster:
kubectl port-forward -n microservices-demo svc/api-gateway 8080:8080
```

Then the same `curl` commands above work against `http://localhost:8080`.

Scale a service without touching any config:

```bash
kubectl scale deployment inventory-service -n microservices-demo --replicas=3
```

Tear down with `kubectl delete -R -f k8s/`.

## Monitoring with Prometheus and Grafana

The monitoring manifests are in `k8s/monitoring/`. Prometheus discovers pods in
the `microservices-demo` namespace by their scrape annotations. The three
application Deployments already expose:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8081" # 8082 for order-service, 8080 for api-gateway
```

Apply the monitoring stack after creating the namespace:

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -R -f k8s/monitoring/
kubectl get pods -n microservices-demo
```

Open the UIs with port forwarding:

```bash
kubectl port-forward -n microservices-demo svc/prometheus 9090:9090
kubectl port-forward -n microservices-demo svc/grafana 3000:3000
```

Visit `http://localhost:9090` for Prometheus and `http://localhost:3000` for
Grafana. The demo Grafana login is `admin` / `admin`; change it before using
this outside a local cluster. Grafana is provisioned with Prometheus and a
`Microservices Demo` dashboard containing request rate, JVM memory, service
instance count, and process uptime panels.

Check discovery in Prometheus under **Status > Service Discovery**, or query:

```promql
up{namespace="microservices-demo"}
```

Prometheus uses `emptyDir` storage in this learning setup, so its history is
lost when the pod is recreated. Add a PersistentVolumeClaim before relying on
long-term metrics. Grafana dashboard and datasource definitions are mounted
from ConfigMaps and are recreated automatically by Kubernetes.

## Actuator / metrics (for later Prometheus + Grafana setup)

Each service exposes:
- `/actuator/health`
- `/actuator/prometheus`

e.g. `curl http://localhost:8081/actuator/prometheus`

## Suggested next steps (your practice checklist)

1. **Dockerfiles** — already added (multi-stage Maven build → JRE runtime).
2. **Kubernetes** — already added in `k8s/`: Deployments in `k8s/deployments/`
   and Services in `k8s/services/` (one file each), plus a ConfigMap holding
   `INVENTORY_SERVICE_URL` / `ORDER_SERVICE_URL` (in k8s these become
   `http://inventory-service:8081` / `http://order-service:8082` using
   Kubernetes' built-in service DNS — no gateway code changes needed).
3. **Helm** — turn the k8s YAML into a chart with `values.yaml` controlling
   image tag/replica count per service.
4. **Monitoring** — add a `ServiceMonitor` (Prometheus Operator) pointing at
   `/actuator/prometheus` for each service, then build a Grafana dashboard.
