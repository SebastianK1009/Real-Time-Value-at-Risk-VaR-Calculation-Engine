module "eks" {
  source = "./eks"

  region          = var.region
  cluster_name    = var.cluster_name
  eks_version     = var.eks_version
  node_instance_type = var.node_instance_type
}
