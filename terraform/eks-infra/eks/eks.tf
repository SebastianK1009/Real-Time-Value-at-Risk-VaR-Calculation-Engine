module "eks_cluster" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.8.4"

  cluster_name    = var.cluster_name
  cluster_version = var.eks_version
  cluster_endpoint_public_access = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Enables OIDC provider for IRSA automatically to let your applications securely access AWS services
  # Allows pods to assume IAM roles using Kubernetes Service Accounts
  # Uses EKS OIDC provider to establish trust between Kubernetes and AWS IAM
  enable_irsa = true
  
  # Enable cluster creator admin permissions
  # Automatically mapped the IAM identity running Terraform to system:masters in Kubernetes
  # Updated the aws-auth ConfigMap to include your user with cluster-admin privileges
  # Without this, EKS have no record of your IAM user in its aws-auth ConfigMap
  enable_cluster_creator_admin_permissions = true

  eks_managed_node_groups = {
    general = {
      instance_types = [var.node_instance_type]
      min_size       = 1
      max_size       = 3
      desired_size   = 2
      capacity_type  = "ON_DEMAND"
    }
  }

  tags = {
    Environment = "dev"
    Project     = "real-time-risk"
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.1.2"

  name = "${var.cluster_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = slice(data.aws_availability_zones.available.names, 0, 3)
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway = true
  single_nat_gateway = false
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# Fetch available availability zones in the specified region
data "aws_availability_zones" "available" {}
