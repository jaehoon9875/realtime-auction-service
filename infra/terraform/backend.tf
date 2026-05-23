terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  # 실행: terraform init -backend-config="bucket=${TFSTATE_BUCKET}"  # 예: {PROJECT_ID}-tfstate
  backend "gcs" {
    prefix = "terraform/state"
  }
}
