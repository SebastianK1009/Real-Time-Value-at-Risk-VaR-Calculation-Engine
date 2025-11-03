#########################################
# IAM policy for ALB controller
# Allow the service account to assume this IAM role using its OIDC identity
#########################################

data "aws_iam_policy_document" "alb_controller_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    # Only allow the specific service account in the kube-system namespace to assume this role
    condition {
      test     = "StringEquals"
      variable = "${replace(module.eks_cluster.cluster_oidc_issuer_url, "https://", "")}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    # The OIDC provider created by the EKS module, ensures only tokens issued by this provider are trusted
    principals {
      type        = "Federated"
      identifiers = [module.eks_cluster.oidc_provider_arn]
    }
  }
}

# Create the IAM role for the ALB controller
resource "aws_iam_role" "alb_controller" {
  name               = "${var.cluster_name}-alb-role"
  assume_role_policy = data.aws_iam_policy_document.alb_controller_assume_role.json
}

#########################################
# Create the AWS Load Balancer Controller IAM Policy
# (official policy from AWS Load Balancer Controller documentation)
#########################################

resource "aws_iam_policy" "alb_controller_policy" {
  name        = "AWSLoadBalancerControllerIAMPolicy"
  description = "Permissions for the AWS Load Balancer Controller"

  policy = file("${path.module}/iam_policy.json")
}

#########################################
# Attach the IAM Policy to the ALB Controller Role
#########################################

resource "aws_iam_role_policy_attachment" "alb_attach" {
  role       = aws_iam_role.alb_controller.name
  policy_arn = aws_iam_policy.alb_controller_policy.arn
}

#########################################
# Helm Deployment of AWS Load Balancer Controller
#########################################

resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"

  set {
    name  = "clusterName"
    value = module.eks_cluster.cluster_name
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  # Add the IAM role annotation to the service account so that it can assume the role
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = aws_iam_role.alb_controller.arn
  }

  set {
    name  = "region"
    value = var.region
  }

  set {
    name  = "vpcId"
    value = module.vpc.vpc_id
  }

  # Cost optimization: Run single replica in dev environment
  set {
    name  = "replicaCount"
    value = "1"
  }

  depends_on = [
    module.eks_cluster,
    aws_iam_role_policy_attachment.alb_attach
  ]
}
