output "auction_db_private_ip" {
  value     = google_sql_database_instance.auction.private_ip_address
  sensitive = true
}

output "bid_db_private_ip" {
  value     = google_sql_database_instance.bid.private_ip_address
  sensitive = true
}

output "user_db_private_ip" {
  value     = google_sql_database_instance.user.private_ip_address
  sensitive = true
}

output "auction_db_connection_name" {
  value = google_sql_database_instance.auction.connection_name
}

output "bid_db_connection_name" {
  value = google_sql_database_instance.bid.connection_name
}

output "user_db_connection_name" {
  value = google_sql_database_instance.user.connection_name
}
