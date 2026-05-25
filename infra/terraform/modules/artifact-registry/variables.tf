variable "project_id" { type = string }
variable "region" {
  type = string
  validation {
    condition     = var.region == "asia-northeast3"
    error_message = "region은 asia-northeast3(서울)만 허용합니다."
  }
}
