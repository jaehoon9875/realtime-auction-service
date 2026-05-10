# PR#10 CodeRabbit 리뷰 지적 사항 (임시)

> **대상 PR**: feat: M5 Kafka Streams App (auction-streams) 구현  
> **문서 정리**: 미처리 / 처리 완료 분리 (브랜치 `feature/m5-kafka-streams` 기준, 2026-05-09)

---

## 처리 완료

아래는 현재 코드베이스에 반영된 항목이다. (원래 번호 유지)

### 1. Schema Registry 기본 포트 불일치
- **파일**: `streams/auction-streams/src/main/resources/application.yml`
- **조치**: `schema.registry.url` 기본값을 `http://localhost:${SCHEMA_REGISTRY_PORT:8085}` 형태로 compose 노출 포트와 정렬함.

### 2. application.server 기본값이 localhost
- **파일**: `streams/auction-streams/src/main/resources/application.yml`, `LocalInteractiveQueryHost`
- **조치**: `application.server`를 `${KAFKA_STREAMS_APPLICATION_SERVER}`만 사용(기본값 제거). `local` 프로필이 아니면 `localhost` 금지 검증 추가.

### 3. Debezium Schema Registry fallback이 localhost
- **파일**: `infra/debezium/register-connectors.sh`
- **조치**: 기본값을 `http://schema-registry:8081` 등 Connect 컨테이너에서 해석 가능한 DNS로 변경.

### 5. KafkaStreams SmartLifecycle 미구현
- **파일**: `streams/auction-streams/src/main/java/.../config/KafkaStreamsLifecycleManager.java` (신규)
- **조치**: `SmartLifecycle` 구현, 종료 시 `KafkaStreams.close(Duration.ofSeconds(30))` 등 graceful shutdown. (PR·CodeRabbit에서 Schema Registry·이 항목은 답글로 해소 확인됨.)

### 4. Avro 버전 & Gradle 플러그인 호환성 충돌
- **파일**: `streams/auction-streams/build.gradle.kts`
- **조치**: PR 리뷰에서 정책·근거로 소명 후 resolve(추가 코드 변경 없음). `build.gradle.kts` 상단에 Avro·플러그인 버전 주석은 유지.

### 6. RestClient 타임아웃 미설정
- **파일**: `RestClientConfig.java`, `AuctionStreamsProperties`, `application.yml`, `infra/.env.example`
- **조치**: `SimpleClientHttpRequestFactory`로 connect/read 타임아웃 적용. 기본값 2000ms/3000ms(`AUCTION_STREAMS_IQ_PEER_*`), auction-service·bid-service의 auction-streams 호출과 동일.

### 16. 테스트에서 Instant.now() 사용
- **파일**: `AuctionStreamsTopologyTest.java`
- **조치**: `BASE_TIME`으로 `occurredAt`·과거 `endsAt` 고정. 미마감용 `endsAt`은 `TopologyTestDriver` wall-clock이 호스트 시각 기준이라 `2099-06-15` 고정(`FAR_FUTURE_ENDS_AT`)으로 오만료 방지.

### 17. kafka.md 블록쿼트 내 빈 줄
- **파일**: `docs/kafka.md`
- **조치**: markdownlint MD028 대응(인용 블록 정리). 이미 반영됨.

### 13. 환경변수 예시 키 오류
- **파일**: `AuctionStreamsProperties.java`, `application.yml`, `infra/.env.example`
- **조치**: 클래스 주석에 relaxed binding 키 명시. YAML은 `AUCTION_STREAMS_*` 우선·구 `PUNCTUATOR_INTERVAL_SECONDS` 등 fallback.

### 14. 포트 파싱 예외 미래핑
- **파일**: `LocalInteractiveQueryHost.java`
- **조치**: port `Integer.parseInt` 실패 시 `IllegalStateException`으로 래핑.

### 10. AuctionMetadata 필드가 설계 계약과 불일치
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/store/AuctionMetadata.java`
- **조치**: `sellerId` 제거. 현재 필드는 `{endsAt, startPrice, title}` — Punctuator 마감 판정(`endsAt`)·시드 정책(`startPrice`)·알림 payload(`title`)에 필요한 필드만 유지.

### 12. BidStreamsTopology 미구현
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/topology/BidStreamsTopology.java`
- **조치**: 토폴로지 구현 완료. BidStateProcessor(별도 파일) 분리. `bid-events` → BID_PLACED 필터 → BidStateProcessor(auction-highest-bid 갱신·OUTBID 알림) → `notification-events`. 1분 tumbling window 급증 탐지(로깅) 브랜치 추가. BidStreamsTopologyTest 7개 케이스 통과.

### 9. peer 에러 코드 일괄 502 변환
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/service/StateQueryService.java`
- **조치**: `RestClientResponseException` 발생 시 `e.getStatusCode()`로 원본 상태 코드 전달. 404만 별도 처리, 나머지(429·503 등)는 peer 상태 그대로 반환.

### 7. DLQ 전송 fire-and-forget
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/exception/DlqExceptionHandler.java`
- **조치**: `producer.send(dlqRecord, callback)` Callback 람다로 변경. 브로커 비동기 실패 시 에러 로그 출력.

### 8. targetUserId에 경매 ID 할당 (스키마 의미 위반)
- **파일**: `infra/avro/NotificationEvent.avsc`, `AuctionMetadataProcessor.java`, `BidStateProcessor.java`
- **조치**: `targetUserId` nullable union 변환 + `targetAuctionId` 필드 추가. `AUCTION_CLOSED`는 `targetAuctionId(auctionId)` + `targetUserId(null)`, `AUCTION_WON`·`OUTBID`는 `targetUserId` 유지. `docs/kafka.md` 라우팅 테이블 업데이트.

### 11. AuctionStreamsTopology event null 체크 누락
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/topology/AuctionStreamsTopology.java`
- **조치**: `filter` 조건에 `event != null &&` null 가드 추가.

### 15. DLQ 토픽 분기에 startsWith 사용
- **파일**: `DlqExceptionHandler.java`
- **조치**: `TOPIC_BID_EVENTS` / `TOPIC_AUCTION_EVENTS`와 `equals` 비교.

---

## 미처리 항목 없음 — 전체 처리 완료 (2026-05-10)
