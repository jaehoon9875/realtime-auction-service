variable "project_id"                 { type = string }
variable "region"                     { type = string }
variable "network"                    { type = string }
variable "private_network_connection" {
  description = "google_service_networking_connection 의존성 전달용"
  type        = any
}
