---
status: "accepted"
date: 2026-05-18
decision-makers: jaehoon9875
---

# Resilience4j Circuit Breaker로 서비스 간 장애 격리

## Context and Problem Statement

Bid Service는 입찰 검증을 위해 Auction Service에 REST 요청으로 경매 정보를 조회합니다.
Auction Service 장애 또는 응답 지연 시 Bid Service의 요청 스레드가 점유된 채 대기하고, 이 현상이 누적되어 Bid Service 전체 장애로 전파될 수 있습니다.

**서비스 간 REST 호출에서 한 서비스의 장애가 다른 서비스로 전파되는 것을 어떻게 차단하는가?**

## Decision Drivers

- 장애 격리: Auction Service 장애가 Bid Service로 전파되지 않아야 함
- 빠른 실패(Fail Fast): 장애 중인 서비스에 불필요하게 요청을 계속 보내지 않아야 함
- Spring Boot와의 통합 용이성

## Considered Options

- Resilience4j Circuit Breaker
- 단순 타임아웃(Timeout)만 적용
- Netflix Hystrix

## Decision Outcome

Chosen option: **"Resilience4j Circuit Breaker"**, because Spring Boot 3.x와 네이티브 통합을 지원하고, Netflix Hystrix가 deprecated된 상황에서 Spring Cloud Circuit Breaker의 공식 구현체이며, Circuit Breaker + Retry + TimeLimiter를 조합하여 세밀한 장애 대응 전략을 설정할 수 있기 때문.

```
Bid Service → [Circuit Breaker] → Auction Service
                   ↑
         CLOSED: 정상 요청 통과
         OPEN: 즉시 fallback 반환 (Auction Service에 요청 안 함)
         HALF_OPEN: 일부 요청으로 복구 여부 확인
```

### Consequences

- Good, because Auction Service 장애 시 Circuit이 OPEN되어 Bid Service 스레드 점유 없이 즉시 실패 처리
- Good, because Spring Boot Actuator와 통합되어 Circuit 상태 모니터링 가능 (`/actuator/health`, `/actuator/circuitbreakers`)
- Bad, because Circuit Breaker 상태(OPEN/CLOSED) 설정 튜닝 필요 (실패율 임계값, 슬라이딩 윈도우 크기 등)
- Bad, because fallback 응답 정책을 명시적으로 정의해야 함

### Confirmation

Auction Service 중단 상태에서 Bid Service의 입찰 요청 시 Circuit Breaker fallback이 동작하고, Actuator에서 Circuit 상태가 OPEN으로 표시됨을 확인.

## Pros and Cons of the Options

### Resilience4j Circuit Breaker

- Good, because Spring Boot 3.x / Spring Cloud와 네이티브 통합, 별도 서버 불필요
- Good, because Circuit Breaker, Retry, RateLimiter, TimeLimiter를 조합 가능
- Good, because Netflix Hystrix의 공식 대체제로 커뮤니티 지원 활발
- Bad, because 초기 설정(슬라이딩 윈도우, 실패율 임계값 등) 튜닝 필요
- Neutral, because 어노테이션(`@CircuitBreaker`) 또는 함수형 API 중 선택하여 사용 가능

### 단순 타임아웃(Timeout)만 적용

- Good, because 구현이 단순하고 추가 라이브러리 불필요
- Bad, because 타임아웃은 장애 중인 서비스에 계속 요청을 보내므로 스레드 점유 지속
- Bad, because 연속 장애 시 요청이 계속 누적되어 Bid Service 전체 과부하 발생 가능
- Bad, because 장애 서비스 복구 판단 로직이 없음

### Netflix Hystrix

- Neutral, because Circuit Breaker 개념을 대중화한 라이브러리
- Bad, because 2018년 유지보수 중단(deprecated) 선언, 신규 프로젝트 사용 부적합
- Bad, because Spring Boot 3.x와 호환성 미보장
