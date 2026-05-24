variable "project_id"            { type = string }
variable "region" {
  type = string
  validation {
    condition     = var.region == "asia-northeast3"
    error_message = "region은 asia-northeast3(서울)만 허용합니다."
  }
}
variable "zone" {
  type = string
  validation {
    condition     = can(regex("^asia-northeast3-[a-c]$", var.zone))
    error_message = "zone은 asia-northeast3-a, b, c 중 하나여야 합니다."
  }
}
variable "network"               { type = string }
variable "subnetwork"            { type = string }
variable "pods_range_name"       { type = string }
variable "services_range_name"   { type = string }
