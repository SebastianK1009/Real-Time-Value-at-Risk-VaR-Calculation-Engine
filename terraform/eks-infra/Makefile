REGION := ap-southeast-1
CLUSTER_NAME := realtime-risk-dev
APPS_DIR := ../../apps

.PHONY: up down plan cost deploy-helm deploy-raw deploy uninstall-helm undeploy-raw verify update-kubeconfig

# Update kubeconfig with current cluster endpoint (prevents stale endpoint errors)
update-kubeconfig:
	aws eks update-kubeconfig --name $(CLUSTER_NAME) --region $(REGION)
	@echo "Kubeconfig updated successfully."

# Default up uses Helm
up: up-helm

# Up utilizing Helm Charts
up-helm:
	terraform init
	terraform apply -auto-approve
	aws eks update-kubeconfig --name $(CLUSTER_NAME) --region $(REGION)
	@echo "Cluster is ready! Deploying services via Helm..."
	$(MAKE) deploy-helm
	@echo "Global deployment via Helm complete."

# Up utilizing Raw Manifests (deployment.yaml)
up-raw:
	terraform init
	terraform apply -auto-approve
	aws eks update-kubeconfig --name $(CLUSTER_NAME) --region $(REGION)
	@echo "Cluster is ready! Deploying services via Raw Manifests..."
	$(MAKE) deploy-raw
	@echo "Global deployment via Raw Manifests complete."

# Deploy using Helm Charts
deploy-helm: update-kubeconfig
	@echo "Deploying Services via Helm..."
	# Cleanup raw manifests to avoid conflicts
	$(MAKE) undeploy-raw || true
	
	helm upgrade --install market-data-simulator $(APPS_DIR)/market-data-simulator/chart/market-data-simulator --wait
	helm upgrade --install market-ingestion-service $(APPS_DIR)/market-ingestion-service/chart/market-ingestion-service --wait
	# Note: ConfigMap is included in the risk-stream-processor chart now
	helm upgrade --install risk-stream-processor $(APPS_DIR)/risk-stream-processor/chart/risk-stream-processor --wait
	helm upgrade --install trade-data-simulator $(APPS_DIR)/trade-data-simulator/chart/trade-data-simulator --wait
	helm upgrade --install trade-ingestion-service $(APPS_DIR)/trade-ingestion-service/chart/trade-ingestion-service --wait
	@echo "All services deployed via Helm."

# Deploy using Raw Manifests (deployment.yaml)
deploy-raw: update-kubeconfig
	@echo "Deploying Services via Raw Manifests..."
	# Cleanup helm releases to avoid conflicts
	$(MAKE) uninstall-helm || true

	kubectl apply -f $(APPS_DIR)/market-data-simulator/k8s/deployment.yaml
	kubectl apply -f $(APPS_DIR)/market-ingestion-service/k8s/deployment.yaml
	kubectl apply -f $(APPS_DIR)/risk-stream-processor/k8s/configmap.yaml
	kubectl apply -f $(APPS_DIR)/risk-stream-processor/k8s/deployment.yaml
	kubectl apply -f $(APPS_DIR)/trade-data-simulator/k8s/deployment.yaml
	kubectl apply -f $(APPS_DIR)/trade-ingestion-service/k8s/deployment.yaml
	@echo "All services deployed via Raw Manifests."

# Cleanup Helm Releases
uninstall-helm:
	helm uninstall market-data-simulator || true
	helm uninstall market-ingestion-service || true
	helm uninstall risk-stream-processor || true
	helm uninstall trade-data-simulator || true
	helm uninstall trade-ingestion-service || true
	@echo "All Helm releases uninstalled."

# Cleanup Raw Manifests
undeploy-raw:
	kubectl delete deployment market-data-simulator market-ingestion-service risk-stream-processor trade-data-simulator trade-ingestion-service 2>/dev/null || true
	kubectl delete service market-data-simulator market-ingestion-service risk-stream-processor trade-data-simulator trade-ingestion-service 2>/dev/null || true
	kubectl delete configmap risk-logging-config 2>/dev/null || true
	@echo "All Raw Manifest resources deleted."

# Full Tear Down (defaults to uninstalling everything before destroying infra)
down: uninstall-helm undeploy-raw
	terraform destroy -auto-approve
	@echo "Cluster destroyed. Billing stopped."

plan:
	terraform plan

cost:
	# Requires infracost installation: brew install infracost
	infracost breakdown --path .
	
verify: update-kubeconfig
	kubectl get pods
	kubectl get services
