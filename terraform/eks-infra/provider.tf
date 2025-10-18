terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.31"
    }
  }
}

provider "aws" {
  region = var.region
  shared_credentials_files = ["~/.aws/credentials"]
  profile                  = "sebastian"
}

# These two will connect Terraform to your EKS cluster after it's created
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data) # cluster_certificate_authority_data is from eks module output
  token                  = data.aws_eks_cluster_auth.cluster.token
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    token                  = data.aws_eks_cluster_auth.cluster.token
  }
}

# Fetch information about the current AWS user
data "aws_caller_identity" "current" {}

# Fetch authentication token for the EKS cluster
data "aws_eks_cluster_auth" "cluster" {
  name = module.eks.cluster_name
}
