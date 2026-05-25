# ──────────────────────────────────────────────────────────────
# Auction DB — Debezium CDC 필수: logical_decoding=on
# ──────────────────────────────────────────────────────────────
resource "google_sql_database_instance" "auction" {
  name             = "auction-db"
  database_version = "POSTGRES_17"
  region           = var.region
  project          = var.project_id

  # private_vpc_connection 생성 완료 후 인스턴스 프로비저닝
  depends_on = [var.private_network_connection]

  settings {
    tier = "db-f1-micro"

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network
    }

    # Debezium WAL 논리 복제 활성화
    database_flags {
      name  = "cloudsql.logical_decoding"
      value = "on"
    }

    database_flags {
      name  = "max_replication_slots"
      value = "10"
    }

    database_flags {
      name  = "max_wal_senders"
      value = "10"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = false
      start_time                     = "03:00"
    }
  }

  # 실수에 의한 삭제 방지. 삭제 시 false로 변경 후 apply → destroy 순서로 진행
  deletion_protection = true
}

resource "google_sql_database" "auction_db" {
  name     = "auction_db"
  instance = google_sql_database_instance.auction.name
  project  = var.project_id
}

resource "google_sql_user" "auction_app" {
  name     = "app"
  instance = google_sql_database_instance.auction.name
  password = var.app_db_password
  project  = var.project_id
}

# Debezium 전용 사용자 (REPLICATION 권한은 Cloud SQL init script에서 부여)
resource "google_sql_user" "auction_debezium" {
  name     = "debezium"
  instance = google_sql_database_instance.auction.name
  password = var.debezium_db_password
  project  = var.project_id
}

# ──────────────────────────────────────────────────────────────
# Bid DB — Debezium CDC 필수: logical_decoding=on
# ──────────────────────────────────────────────────────────────
resource "google_sql_database_instance" "bid" {
  name             = "bid-db"
  database_version = "POSTGRES_17"
  region           = var.region
  project          = var.project_id

  depends_on = [var.private_network_connection]

  settings {
    tier = "db-f1-micro"

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network
    }

    database_flags {
      name  = "cloudsql.logical_decoding"
      value = "on"
    }

    database_flags {
      name  = "max_replication_slots"
      value = "10"
    }

    database_flags {
      name  = "max_wal_senders"
      value = "10"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = false
      start_time                     = "03:00"
    }
  }

  # 실수에 의한 삭제 방지. 삭제 시 false로 변경 후 apply → destroy 순서로 진행
  deletion_protection = true
}

resource "google_sql_database" "bid_db" {
  name     = "bid_db"
  instance = google_sql_database_instance.bid.name
  project  = var.project_id
}

resource "google_sql_user" "bid_app" {
  name     = "app"
  instance = google_sql_database_instance.bid.name
  password = var.app_db_password
  project  = var.project_id
}

resource "google_sql_user" "bid_debezium" {
  name     = "debezium"
  instance = google_sql_database_instance.bid.name
  password = var.debezium_db_password
  project  = var.project_id
}

# ──────────────────────────────────────────────────────────────
# User DB — Debezium 미사용, 표준 설정
# ──────────────────────────────────────────────────────────────
resource "google_sql_database_instance" "user" {
  name             = "user-db"
  database_version = "POSTGRES_17"
  region           = var.region
  project          = var.project_id

  depends_on = [var.private_network_connection]

  settings {
    tier = "db-f1-micro"

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = false
      start_time                     = "03:00"
    }
  }

  # 실수에 의한 삭제 방지. 삭제 시 false로 변경 후 apply → destroy 순서로 진행
  deletion_protection = true
}

resource "google_sql_database" "user_db" {
  name     = "user_db"
  instance = google_sql_database_instance.user.name
  project  = var.project_id
}

resource "google_sql_user" "user_app" {
  name     = "app"
  instance = google_sql_database_instance.user.name
  password = var.app_db_password
  project  = var.project_id
}
