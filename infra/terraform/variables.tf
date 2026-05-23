variable "project_id" {
  description = "GCP 프로젝트 ID"
  type        = string
}

variable "region" {
  description = "GCP 리전"
  type        = string
  default     = "asia-northeast3"
}

variable "zone" {
  description = "GKE Zonal 클러스터 Zone"
  type        = string
  default     = "asia-northeast3-a"
}

variable "app_db_password" {
  description = "Cloud SQL app 사용자 패스워드 (auction·bid·user DB 공통)"
  type        = string
  sensitive   = true
}

variable "debezium_db_password" {
  description = "Debezium 전용 DB 사용자 패스워드"
  type        = string
  sensitive   = true
}

variable "github_org" {
  description = "GitHub 사용자명 또는 조직명 (Workload Identity 조건부 바인딩)"
  type        = string
}

variable "github_repo" {
  description = "GitHub 레포지토리명 (Workload Identity 조건부 바인딩)"
  type        = string
}
