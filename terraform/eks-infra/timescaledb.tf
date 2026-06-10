resource "kubernetes_namespace" "timescaledb" {
  metadata {
    name = "timescaledb"
  }

  depends_on = [
    module.eks
  ]
}

resource "helm_release" "timescaledb" {
  name       = "timescaledb"
  repository = "https://charts.timescale.com"
  chart      = "timescaledb-single" # Correct chart name
  namespace  = kubernetes_namespace.timescaledb.metadata[0].name
  timeout    = 600

  # Wait for the database to be ready
  wait       = true

  values = [
    file("${path.module}/../../infrastructure/timescaledb/values.yaml")
  ]

  depends_on = [
    module.eks,
    kubernetes_namespace.timescaledb
  ]
}

# -----------------------------------------------------------------------------
# Schema Initialization
# -----------------------------------------------------------------------------

# 1. ConfigMap containing the initialization SQL
resource "kubernetes_config_map" "timescaledb_init" {
  metadata {
    name      = "timescaledb-init-schema"
    namespace = kubernetes_namespace.timescaledb.metadata[0].name
  }

  data = {
    "init.sql" = file("${path.module}/../../infrastructure/timescaledb/schema.sql")
  }
}

# 2. Kubernetes Job to run the SQL against the database
resource "kubernetes_job" "timescaledb_schema_loader" {
  metadata {
    name      = "timescaledb-schema-loader"
    namespace = kubernetes_namespace.timescaledb.metadata[0].name
  }

  spec {
    template {
      metadata {
        name = "timescaledb-schema-loader"
      }
      spec {
        container {
          name  = "psql-client"
          image = "postgres:15-alpine" # Lightweight image with psql
          command = ["/bin/sh", "-c"]
          
          # Retry loop to wait for DB connection, then run script
          args = [
            <<EOT
            echo "Waiting for TimescaleDB..."
            until PGPASSWORD=$PGPASSWORD psql -h timescaledb -U $PGUSER -d $PGDATABASE -c '\q'; do
              echo "Database not ready, retrying in 5s..."
              sleep 5
            done
            echo "Connected! Applying schema..."
            PGPASSWORD=$PGPASSWORD psql -h timescaledb -U $PGUSER -d $PGDATABASE -f /etc/config/init.sql
            EOT
          ]

          env {
            name  = "PGUSER"
            value = "postgres" # Superuser defined in values.yaml
          }
          env {
            name  = "PGPASSWORD"
            value = "timescale_password" # defined in values.yaml
          }
          env {
            name  = "PGDATABASE"
            value = "postgres" # Default DB
          }

          volume_mount {
            name       = "schema-volume"
            mount_path = "/etc/config"
          }
        }

        restart_policy = "OnFailure"

        volume {
          name = "schema-volume"
          config_map {
            name = kubernetes_config_map.timescaledb_init.metadata[0].name
          }
        }
      }
    }
    # Clean up the job after success to keep namespace clean
    ttl_seconds_after_finished = 3600 
  }

  depends_on = [
    helm_release.timescaledb,
    kubernetes_config_map.timescaledb_init
  ]
}
