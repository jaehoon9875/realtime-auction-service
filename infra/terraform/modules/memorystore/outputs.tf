output "redis_host" {
  value     = google_redis_instance.cache.host
  sensitive = true
}

output "redis_port" {
  value = google_redis_instance.cache.port
}
