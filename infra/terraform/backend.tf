terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  # 실행: terraform init -backend-config="bucket=realtime-auction-tfstate-jh9875"
  backend "gcs" {
    prefix = "terraform/state"
  }
}
