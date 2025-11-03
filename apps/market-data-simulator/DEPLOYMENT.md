# Market Data Simulator - MVP Deployment

Simple deployment configuration for the TCP Market Data Simulator.

## Prerequisites

- Docker installed and running
- Kubernetes cluster (minikube, kind, or cloud provider)
- kubectl configured
- Helm 3 (optional, if using Helm chart)

## Quick Start

### 1. Build Docker Image

```bash
cd apps/market-data-simulator
docker build -t market-data-simulator:latest .
```

<<<<<<< HEAD
### 2. Deploy to Kubernetes
=======
### 2. Push to Amazon ECR

```bash
# Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=ap-southeast-1

# Create ECR repository (if it doesn't exist)
aws ecr create-repository --repository-name market-data-simulator --region $AWS_REGION 2>/dev/null || true

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Tag and push image
docker tag market-data-simulator:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/market-data-simulator:latest
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/market-data-simulator:latest
```

### 3. Deploy to Kubernetes
>>>>>>> feature/001-iac

**Option A: Using kubectl (Standalone YAML)**

```bash
kubectl apply -f k8s/deployment.yaml
```

**Option B: Using Helm Chart**

```bash
helm install market-data-simulator chart/market-data-simulator
```

### 3. Verify Deployment

```bash
# Check pods
kubectl get pods -l app=market-data-simulator

# Check service
kubectl get svc market-data-simulator

# View logs
kubectl logs -l app=market-data-simulator -f
```

### 4. Test the Simulator

Port-forward to access from your local machine:

```bash
kubectl port-forward svc/market-data-simulator 9999:9999
```

Then connect with the test client:

```bash
python src/test_client.py
```

## Configuration

### Helm Values (values.yaml)

```yaml
<<<<<<< HEAD
replicaCount: 2

image:
  repository: market-data-simulator
=======
# Cost-optimized for small clusters
replicaCount: 1

image:
  repository: 480609332059.dkr.ecr.ap-southeast-1.amazonaws.com/market-data-simulator
>>>>>>> feature/001-iac
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 9999

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 256Mi
```

<<<<<<< HEAD
=======
**Note:** Replace `480609332059` with your AWS account ID.

>>>>>>> feature/001-iac
### Environment Variables

- `MARKET_DATA_HOST`: Bind address (default: "0.0.0.0")
- `MARKET_DATA_PORT`: TCP port (default: "9999")

## Uninstall

**kubectl:**

```bash
kubectl delete -f k8s/deployment.yaml
```

**Helm:**

```bash
helm uninstall market-data-simulator
```

## Architecture

<<<<<<< HEAD
- **Deployment**: 2 replicas for high availability
- **Service**: ClusterIP for internal cluster access
- **Health Checks**: TCP socket probes on port 9999
- **Resources**: CPU and memory limits for stability
=======
- **Deployment**: 1 replica (cost-optimized for small clusters)
- **Service**: ClusterIP for internal cluster access
- **Health Checks**: TCP socket probes on port 9999
- **Resources**: CPU and memory limits for stability
- **Container Registry**: Amazon ECR (free tier: 500 MB storage/month)
>>>>>>> feature/001-iac

## Instruments Streamed

1. AAPL - Apple Inc.
2. GOOGL - Alphabet Inc.
3. MSFT - Microsoft Corporation
4. TSLA - Tesla Inc.
5. AMZN - Amazon.com Inc.
6. SPY - S&P 500 ETF
7. EUR/USD - Euro to US Dollar
8. GBP/USD - British Pound to US Dollar
9. BTC/USD - Bitcoin to US Dollar
10. ETH/USD - Ethereum to US Dollar

## Message Format

```json
{
  "instrument": "AAPL",
  "price": 150.25,
  "timestamp": "2024-01-15T10:30:45.123456+00:00"
}
```

## MVP Simplification

This is a **minimal viable deployment** with only essential components. Advanced features have been removed for simplicity:

**Removed:**

- ServiceAccount, ConfigMap, HPA, Ingress, PodDisruptionBudget
- NetworkPolicy, ResourceQuota, Monitoring (Prometheus)
- Security contexts, affinity rules, advanced probe configs
- Kustomize configuration

**Kept:**

- Basic Deployment (2 replicas) with TCP health checks
- ClusterIP Service for internal access
- Resource limits (CPU/memory)
- Minimal Helm chart with templating

Add back advanced features as needed for production deployments.

<<<<<<< HEAD
=======
## Cost Optimization

This deployment is optimized for cost on small EKS clusters:

### Current Configuration

- **1 replica** instead of 2 (reduces pod count by 50%)
- **ECR free tier** for image storage (500 MB/month free)
- **Small EC2 instances** (t3.small/t3.medium) with pod limits

### Pod Capacity on AWS EKS

AWS EKS limits pods per node based on instance ENI capacity:

- **t3.small**: ~4-8 pods per node
- **t3.medium**: ~8-17 pods per node
- **t3.large**: ~29 pods per node

### Additional Cost Savings

To further reduce costs on small clusters:

```bash
# Scale down AWS Load Balancer Controller (if not using Ingress)
kubectl scale deployment aws-load-balancer-controller -n kube-system --replicas=1

# Scale down CoreDNS (only if cluster has light DNS load)
kubectl scale deployment coredns -n kube-system --replicas=1
```

### Scaling for Production

When ready for production with high availability:

```bash
# Scale up to 2+ replicas
kubectl scale deployment market-data-simulator --replicas=2

# Or update values.yaml and helm upgrade
helm upgrade market-data-simulator chart/market-data-simulator --set replicaCount=2
```

---

>>>>>>> feature/001-iac
---

## 📦 Helm Chart & YAML Structure Explained

### **Helm Chart Structure** (5 files total)

#### 1. **Chart.yaml** - Chart Metadata

Defines basic chart identity. Helm uses this to identify and version your chart.

#### 2. **values.yaml** - Configuration Values (15 lines)

All configurable values in one place. Change replicas, image, or resources without touching templates.

#### 3. **templates/\_helpers.tpl** - Template Functions

Reusable template snippets that generate consistent names and labels across all resources.

**How it works:**

- `{{ include "market-data-simulator.fullname" . }}` → becomes `"market-data-simulator"` (release name)
- `{{- include "market-data-simulator.labels" . | nindent 4 }}` → injects labels with 4-space indent

#### 4. **templates/deployment.yaml** - Pod Deployment

**Key Points:**

- **Replicas:** 2 pods for high availability
- **Health Checks:** TCP probes ensure pods are healthy
- **Resources:** Prevents pods from using unlimited CPU/memory
- **Template Variables:** `{{ .Values.* }}` pulls from values.yaml

#### 5. **templates/service.yaml** - Network Service

**How it works:**

- **ClusterIP:** Service gets internal IP (e.g., `10.96.1.5`)
- **Selector:** Matches pods with `app=market-data-simulator, release=<release-name>`
- **Port Mapping:** `service:9999` → `pod:9999`

### **Standalone YAML** (`k8s/deployment.yaml`)

Non-templated version for users who prefer `kubectl` over Helm.

**Difference from Helm:**

- ❌ No templating (`{{ }}` syntax)
- ❌ No dynamic values
- ✅ Single file for quick `kubectl apply`
- ✅ Same functionality, just static

### **How to Use**

**Option 1: Helm (Flexible)**

```bash
# Install with default values
helm install my-simulator chart/market-data-simulator

# Override values
helm install my-simulator chart/market-data-simulator \
  --set replicaCount=3 \
  --set image.tag=v2.0.0
```

**Option 2: kubectl (Simple)**

```bash
kubectl apply -f k8s/deployment.yaml
```

### **What Happens When You Deploy**

1. **Kubernetes creates:**

   - 2 pods running `market-data-simulator:latest`
   - 1 ClusterIP service at port 9999

2. **Health checks start:**

   - Liveness probe checks every 30s (restarts if fails)
   - Readiness probe checks every 10s (removes from service if fails)

3. **Service routing:**

   - Service DNS: `market-data-simulator.default.svc.cluster.local:9999`
   - Load balances between 2 pod replicas

4. **Resource enforcement:**
   - Each pod guaranteed 100m CPU + 128Mi RAM
   - Each pod limited to 500m CPU + 256Mi RAM
