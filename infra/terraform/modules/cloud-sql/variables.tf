variable "project_id"                  { type = string }
variable "region" {
  type = string
  validation {
    condition     = var.region == "asia-northeast3"
    error_message = "region은 asia-northeast3(서울)만 허용합니다."
  }
}
variable "network"                     { type = string }
variable "app_db_password" {
  type      = string
  sensitive = true
}
variable "debezium_db_password" {
  type      = string
  sensitive = true
}
variable "private_network_connection"  {
  description = "google_service_networking_connection 의존성 전달용"
  type        = any
}
