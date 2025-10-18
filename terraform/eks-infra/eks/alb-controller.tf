# # IAM policy for ALB controller
# data "aws_iam_policy_document" "alb_controller_assume_role" {
#   statement {
#     actions = ["sts:AssumeRoleWithWebIdentity"]
#     effect  = "Allow"

#     condition {
#       test     = "StringEquals"
#       variable = "${replace(module.eks.oidc_provider, "https://", "")}:sub"
#       values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
#     }

#     principals {
#       type        = "Federated"
#       identifiers = [module.eks.oidc_provider_arn]
#     }
#   }
# }

# resource "aws_iam_role" "alb_controller" {
#   name               = "${var.cluster_name}-alb-role"
#   assume_role_policy = data.aws_iam_policy_document.alb_controller_assume_role.json
# }

# # Attach the managed AWSLoadBalancerController policy
# resource "aws_iam_role_policy_attachment" "alb_attach" {
#   role       = aws_iam_role.alb_controller.name
#   policy_arn = "arn:aws:iam::aws:policy/AWSLoadBalancerControllerIAMPolicy"
# }

# # Helm chart deployment
# resource "helm_release" "alb_controller" {
#   name       = "aws-load-balancer-controller"
#   repository = "https://aws.github.io/eks-charts"
#   chart      = "aws-load-balancer-controller"
#   namespace  = "kube-system"

#   set {
#     name  = "clusterName"
#     value = module.eks.cluster_name
#   }

#   set {
#     name  = "serviceAccount.create"
#     value = "true"
#   }

#   set {
#     name  = "serviceAccount.name"
#     value = "aws-load-balancer-controller"
#   }

#   set {
#     name  = "region"
#     value = var.region
#   }

#   set {
#     name  = "vpcId"
#     value = module.vpc.vpc_id
#   }

#   depends_on = [
#     module.eks,
#     aws_iam_role_policy_attachment.alb_attach
#   ]
# }
