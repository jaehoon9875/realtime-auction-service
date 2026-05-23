output "network_name"      { value = google_compute_network.vpc.name }
output "network_self_link" { value = google_compute_network.vpc.self_link }
output "subnetwork_name"   { value = google_compute_subnetwork.subnet.name }
output "pods_range_name"   { value = "pods" }
output "services_range_name" { value = "services" }
output "private_vpc_connection" { value = google_service_networking_connection.private_vpc_connection }
