variable "project_id"          { type = string }
variable "region" {
  type = string
  validation {
    condition     = var.region == "asia-northeast3"
    error_message = "region은 asia-northeast3(서울)만 허용합니다."
  }
}
variable "gke_node_sa_email"   { type = string }
variable "registry_id"         { type = string }
variable "github_org"          { type = string }
variable "github_repo"         { type = string }
