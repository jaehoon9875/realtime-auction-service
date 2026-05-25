# ──────────────────────────────────────────────────────────────
# GKE 노드 SA — Artifact Registry 이미지 Pull 권한
# ──────────────────────────────────────────────────────────────
resource "google_artifact_registry_repository_iam_member" "node_ar_reader" {
  project    = var.project_id
  location   = var.region
  repository = var.registry_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${var.gke_node_sa_email}"
}

# ──────────────────────────────────────────────────────────────
# GitHub Actions — Workload Identity Federation
# key.json 없이 GitHub OIDC 토큰으로 GCP 인증
# ──────────────────────────────────────────────────────────────
resource "google_iam_workload_identity_pool" "github" {
  project                   = var.project_id
  workload_identity_pool_id = "github-pool"
  display_name              = "GitHub Actions Pool"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-provider"
  display_name                       = "GitHub Actions OIDC Provider"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.actor"      = "assertion.actor"
    "attribute.repository" = "assertion.repository"
  }

  # 지정 레포에서 발급된 토큰만 허용
  attribute_condition = "attribute.repository == '${var.github_org}/${var.github_repo}'"
}

resource "google_service_account" "github_actions" {
  account_id   = "github-actions-sa"
  display_name = "GitHub Actions Service Account"
  project      = var.project_id
}

resource "google_service_account_iam_member" "github_actions_wi_binding" {
  service_account_id = google_service_account.github_actions.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_org}/${var.github_repo}"
}

resource "google_artifact_registry_repository_iam_member" "github_actions_ar_writer" {
  project    = var.project_id
  location   = var.region
  repository = var.registry_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.github_actions.email}"
}

# ──────────────────────────────────────────────────────────────
# External Secrets Operator — Secret Manager 접근
# ESO K8s SA → GCP SA 위임 (Workload Identity)
# ──────────────────────────────────────────────────────────────
resource "google_service_account" "eso" {
  account_id   = "external-secrets-sa"
  display_name = "External Secrets Operator Service Account"
  project      = var.project_id
}

resource "google_project_iam_member" "eso_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.eso.email}"
}

# K8s SA: external-secrets-system/external-secrets → GCP SA 위임
resource "google_service_account_iam_member" "eso_wi_binding" {
  service_account_id = google_service_account.eso.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[external-secrets-system/external-secrets]"
}

# ──────────────────────────────────────────────────────────────
# Debezium — Cloud SQL Auth Proxy Workload Identity
# ──────────────────────────────────────────────────────────────
resource "google_service_account" "debezium" {
  account_id   = "debezium"
  display_name = "Debezium Service Account"
  project      = var.project_id
}

resource "google_project_iam_member" "debezium_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.debezium.email}"
}

resource "google_service_account_iam_member" "debezium_wi_binding" {
  service_account_id = google_service_account.debezium.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[auction/debezium-serviceaccount]"
}
