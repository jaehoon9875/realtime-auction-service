provider "google" {
  project = var.project_id
  region  = var.region
}

module "vpc" {
  source     = "./modules/vpc"
  project_id = var.project_id
  region     = var.region
}

module "gke" {
  source              = "./modules/gke"
  project_id          = var.project_id
  region              = var.region
  zone                = var.zone
  network             = module.vpc.network_name
  subnetwork          = module.vpc.subnetwork_name
  pods_range_name     = module.vpc.pods_range_name
  services_range_name = module.vpc.services_range_name
}

module "cloud_sql" {
  source                     = "./modules/cloud-sql"
  project_id                 = var.project_id
  region                     = var.region
  network                    = module.vpc.network_self_link
  app_db_password            = var.app_db_password
  debezium_db_password       = var.debezium_db_password
  private_network_connection = module.vpc.private_vpc_connection
}

module "memorystore" {
  source                     = "./modules/memorystore"
  project_id                 = var.project_id
  region                     = var.region
  network                    = module.vpc.network_self_link
  private_network_connection = module.vpc.private_vpc_connection
}

module "artifact_registry" {
  source     = "./modules/artifact-registry"
  project_id = var.project_id
  region     = var.region
}

module "iam" {
  source               = "./modules/iam"
  project_id           = var.project_id
  region               = var.region
  gke_node_sa_email    = module.gke.node_service_account_email
  registry_id          = module.artifact_registry.registry_id
  github_org           = var.github_org
  github_repo          = var.github_repo

  # eso_wi_binding이 참조하는 {project}.svc.id.goog pool은 GKE 클러스터 생성 후 자동 생성됨
  depends_on = [module.gke]
}
