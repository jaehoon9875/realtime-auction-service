resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "auction-images"
  format        = "DOCKER"
  project       = var.project_id

  description = "realtime-auction-service 서비스 이미지 저장소"
}
