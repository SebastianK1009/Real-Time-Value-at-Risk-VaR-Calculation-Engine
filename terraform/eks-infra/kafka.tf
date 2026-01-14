resource "kubernetes_namespace" "kafka" {
  metadata {
    name = "kafka"
  }
}

resource "helm_release" "kafka" {
  name       = "kafka-cluster"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "kafka"
  namespace  = kubernetes_namespace.kafka.metadata[0].name
  version    = "29.3.14" # Pinned to the version that was working previously
  timeout    = 600

  # We use the existing values.yaml from the infrastructure folder
  # This ensures the configuration (image, resources, storage) stays consistent
  values = [
    file("${path.module}/../../infrastructure/kafka/values.yaml")
  ]

  # Ensure EKS is fully ready before deploying
  depends_on = [
    module.eks
  ]
}
