-- User Service DB 초기 설정
-- Debezium CDC를 사용하지 않으므로 복제 설정 불필요
-- 애플리케이션 계정에 필요한 권한만 부여
GRANT ALL PRIVILEGES ON DATABASE user_db TO CURRENT_USER;
