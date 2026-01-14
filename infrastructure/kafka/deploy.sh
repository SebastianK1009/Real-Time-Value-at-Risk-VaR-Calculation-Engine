#!/bin/bash
set -e

# Add Bitnami repo
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Create namespace if it doesn't exist
kubectl create namespace kafka --dry-run=client -o yaml | kubectl apply -f -

# Deploy Kafka
echo "Deploying Kafka Cluster..."
helm upgrade --install kafka-cluster bitnami/kafka \
  --namespace kafka \
  --values values.yaml \
  --wait

echo "Deployment complete! You can connect to the cluster at: kafka-cluster.kafka.svc.cluster.local:9092"
