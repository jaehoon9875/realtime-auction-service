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

---

## 미처리 (Major / Minor)

### 7. DLQ 전송 fire-and-forget
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/exception/DlqExceptionHandler.java`
- **문제**: `producer.send(dlqRecord)` 후 즉시 `CONTINUE` → 브로커 비동기 실패 탐지 불가
- **수정**: `producer.send(dlqRecord).get()` 또는 Callback으로 실패 로깅

### 8. targetUserId에 경매 ID 할당 (스키마 의미 위반)
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/processor/AuctionMetadataProcessor.java`
- **문제**: `setTargetUserId(auctionId)` — notification-service가 사용자 ID로 해석 시 오작동 가능
- **수정**: 스키마에 라우팅 필드 추가, 또는 `targetUserId` 미설정·`auctionId`만 사용하는 계약 정리

### 9. peer 에러 코드 일괄 502 변환
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/service/StateQueryService.java`
- **문제**: 404 외 peer 에러를 502로 통일 → 429/503 등 원본 상태 손실
- **수정**: `RestClientResponseException` 시 `e.getStatusCode()` 전달

### 10. AuctionMetadata 필드가 설계 계약과 불일치
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/store/AuctionMetadata.java`
- **문제**: `{endsAt, startPrice, sellerId, title}` vs 설계 `{closedAt, status}` 등 계약 불일치
- **수정**: 메타데이터 모델·토폴로지·스토어 사용처 일괄 정리

### 11. AuctionStreamsTopology event null 체크 누락
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/topology/AuctionStreamsTopology.java`
- **문제**: `event.getEventType()` 전 null 가드 없음
- **수정**: `event != null && EVENT_AUCTION_CREATED.equals(event.getEventType())` 등

### 12. BidStreamsTopology 미구현
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/topology/BidStreamsTopology.java`
- **문제**: 클래스가 비어 있음 → `bid-events` → 최고가 스토어 갱신 토폴로지 부재
- **수정**: 토폴로지 구현 또는 로직이 다른 클래스에만 있으면 파일 정리

### 13. 환경변수 예시 키 오류
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/config/AuctionStreamsProperties.java`
- **문제**: 주석 예시가 `PUNCTUATOR_INTERVAL_SECONDS` — 실제 relaxed binding은 `AUCTION_STREAMS_PUNCTUATOR_INTERVAL_SECONDS`
- **수정**: 주석을 실제 키에 맞게 수정

### 14. 포트 파싱 예외 미래핑
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/config/LocalInteractiveQueryHost.java`
- **문제**: `Integer.parseInt` 실패 시 `NumberFormatException` 노출
- **수정**: `IllegalStateException`으로 래핑·메시지 통일

### 15. DLQ 토픽 분기에 startsWith 사용
- **파일**: `streams/auction-streams/src/main/java/com/jaehoon/streams/auction/exception/DlqExceptionHandler.java`
- **문제**: `startsWith("bid")` 등 → 토픽명 확장 시 오매핑
- **수정**: `TOPIC_BID_EVENTS` 등 상수와 `equals` 비교

---

## 우선순위 요약 (미처리 기준)

| 우선순위 | 항목 | 이유 |
|---------|------|------|
| 1순위 | #10 AuctionMetadata 계약 불일치 | State Store·마감 로직 계약 |
| 1순위 | #12 BidStreamsTopology 미구현 | 입찰 스트림 핵심 |
| 1순위 | #8 targetUserId 스키마 위반 | downstream 알림 라우팅 |
| 2순위 | #7 DLQ fire-and-forget | 유실 탐지 |
| 2순위 | #9 peer HTTP 상태 보존 | 멀티 인스턴스 IQ |
| 3순위 | #11, #13–#15 | null 가드·주석·DLQ 분기 등 |
