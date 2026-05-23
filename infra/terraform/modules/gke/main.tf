# GKE 노드 전용 서비스 어카운트 (기본 Compute SA 대신 최소 권한 부여)
resource "google_service_account" "node_sa" {
  account_id   = "gke-node-sa"
  display_name = "GKE Node Service Account"
  project      = var.project_id
}

resource "google_project_iam_member" "node_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.node_sa.email}"
}

resource "google_project_iam_member" "node_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.node_sa.email}"
}

resource "google_project_iam_member" "node_monitoring_viewer" {
  project = var.project_id
  role    = "roles/monitoring.viewer"
  member  = "serviceAccount:${google_service_account.node_sa.email}"
}

# ──────────────────────────────────────────────────────────────
# GKE Standard Zonal 클러스터
# ──────────────────────────────────────────────────────────────
resource "google_container_cluster" "main" {
  name     = "auction-cluster"
  location = var.zone
  project  = var.project_id

  # 기본 노드풀 즉시 삭제 후 커스텀 노드풀로 대체
  remove_default_node_pool = true
  initial_node_count       = 1

  network    = var.network
  subnetwork = var.subnetwork

  networking_mode = "VPC_NATIVE"

  ip_allocation_policy {
    cluster_secondary_range_name  = var.pods_range_name
    services_secondary_range_name = var.services_range_name
  }

  # K8s SA → GCP SA 매핑을 위한 Workload Identity
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  logging_service    = "logging.googleapis.com/kubernetes"
  monitoring_service = "monitoring.googleapis.com/kubernetes"

  # dev 환경 삭제 보호 해제
  deletion_protection = false
}

# ──────────────────────────────────────────────────────────────
# default-pool: 인프라 워크로드 (Kafka, Debezium, Schema Registry, auction-streams)
# - On-demand, e2-standard-4 × 2 (고정)
# - taint 없음: 시스템 Pod 및 인프라 Pod의 기본 착지 풀
# - label workload-type=infra → 인프라 Pod는 nodeSelector로 이 풀만 사용
# ──────────────────────────────────────────────────────────────
resource "google_container_node_pool" "default_pool" {
  name     = "default-pool"
  cluster  = google_container_cluster.main.id
  location = var.zone
  project  = var.project_id

  node_count = 2

  node_config {
    machine_type = "e2-standard-4"

    service_account = google_service_account.node_sa.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    labels = {
      workload-type = "infra"
    }

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }
}

# ──────────────────────────────────────────────────────────────
# spot-pool: 앱 워크로드 (api-gateway, auction, bid, user, notification)
# - Spot, e2-standard-2 × 0~3 (오토스케일)
# - taint spot=true:NoSchedule → 앱 Pod는 반드시 toleration 추가 필요
# - 강제 종료 시 앱은 Stateless이므로 재기동으로 복구
# ──────────────────────────────────────────────────────────────
resource "google_container_node_pool" "spot_pool" {
  name     = "spot-pool"
  cluster  = google_container_cluster.main.id
  location = var.zone
  project  = var.project_id

  autoscaling {
    min_node_count = 0
    max_node_count = 3
  }

  node_config {
    machine_type = "e2-standard-2"
    spot         = true

    service_account = google_service_account.node_sa.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    labels = {
      "cloud.google.com/gke-spot" = "true"
    }

    taint {
      key    = "spot"
      value  = "true"
      effect = "NO_SCHEDULE"
    }

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }
}
