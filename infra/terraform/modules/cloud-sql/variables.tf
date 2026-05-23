variable "project_id"                  { type = string }
variable "region"                      { type = string }
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
