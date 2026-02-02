# TimescaleDB Helm Chart Reference

This directory contains the Helm chart configuration for deploying TimescaleDB in your Kubernetes cluster.

## Usage

1. Add the TimescaleDB Helm repository:
   helm repo add timescale https://charts.timescale.com
   helm repo update

2. Install TimescaleDB using the provided values.yaml:
   helm install timescaledb timescale/timescaledb -f values.yaml

3. For more options, see: https://github.com/timescale/helm-charts
