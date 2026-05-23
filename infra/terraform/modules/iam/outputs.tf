output "github_actions_sa_email" {
  value       = google_service_account.github_actions.email
  description = "GitHub Secrets GCP_SERVICE_ACCOUNT 에 등록"
}

output "workload_identity_provider" {
  value       = google_iam_workload_identity_pool_provider.github.name
  description = "GitHub Secrets GCP_WORKLOAD_IDENTITY_PROVIDER 에 등록"
}

output "eso_sa_email" {
  value = google_service_account.eso.email
}
