output "gke_cluster_name" {
  value = module.gke.cluster_name
}

output "gke_cluster_endpoint" {
  value     = module.gke.cluster_endpoint
  sensitive = true
}

output "artifact_registry_url" {
  value       = module.artifact_registry.registry_url
  description = "docker push 대상 URL. GitHub Secrets REGISTRY_URL 에 등록"
}

output "github_actions_sa_email" {
  value       = module.iam.github_actions_sa_email
  description = "GitHub Secrets GCP_SERVICE_ACCOUNT 에 등록"
}

output "workload_identity_provider" {
  value       = module.iam.workload_identity_provider
  description = "GitHub Secrets GCP_WORKLOAD_IDENTITY_PROVIDER 에 등록"
}

output "cloud_sql_auction_private_ip" {
  value     = module.cloud_sql.auction_db_private_ip
  sensitive = true
}

output "cloud_sql_bid_private_ip" {
  value     = module.cloud_sql.bid_db_private_ip
  sensitive = true
}

output "cloud_sql_user_private_ip" {
  value     = module.cloud_sql.user_db_private_ip
  sensitive = true
}

output "redis_host" {
  value     = module.memorystore.redis_host
  sensitive = true
}

output "redis_port" {
  value = module.memorystore.redis_port
}
