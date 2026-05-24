# notification-service(WebSocket 세션)·user-service(Refresh Token) 공유 Redis 인스턴스
resource "google_redis_instance" "cache" {
  name           = "auction-redis"
  tier           = "BASIC"
  memory_size_gb = 1
  region         = var.region
  project        = var.project_id
  redis_version  = "REDIS_7_0"

  authorized_network = var.network
  connect_mode       = "PRIVATE_SERVICE_ACCESS"
  # depends_on은 모듈 간 의존성이므로 호출부(루트 main.tf)에서 관리
}
