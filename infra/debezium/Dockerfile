# confluentinc/cp-kafka-connect를 베이스로 사용
# - Confluent Avro Converter가 이미 포함되어 있어 Schema Registry 연동 가능
# - confluent-hub CLI로 Debezium PostgreSQL 커넥터를 추가 설치
FROM confluentinc/cp-kafka-connect:7.7.0

# Debezium PostgreSQL 커넥터 설치
# pgoutput 플러그인 기반으로 PostgreSQL WAL을 읽어 Kafka로 발행
RUN confluent-hub install --no-prompt debezium/debezium-connector-postgresql:2.5.4
