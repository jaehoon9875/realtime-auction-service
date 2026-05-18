---
status: "proposed"
date: 2026-05-18
decision-makers: jaehoon9875
---

# Redis 기반 WebSocket 세션 공유

> **Proposed**: M6(Notification Service) 구현 예정. 구현 완료 후 `accepted`로 변경.

## Context and Problem Statement

Notification Service가 다수 인스턴스로 스케일아웃되면, 특정 사용자의 WebSocket 세션이 어느 인스턴스에 연결되어 있는지 알 수 없습니다.
Kafka에서 소비한 알림 이벤트를 받은 인스턴스가 해당 사용자와 연결된 인스턴스가 아닐 수 있습니다.

**멀티 인스턴스 환경에서 올바른 WebSocket 세션으로 알림을 전달하려면 어떻게 해야 하는가?**

## Decision Drivers

- 스케일아웃 지원: 인스턴스 수와 무관하게 정확한 사용자에게 알림 전달
- 세션 상태를 인스턴스 외부에서 관리해야 재시작 시 유실 최소화
- Redis는 ADR-002 검토 과정에서 이미 인프라 도입 가능성을 확인함

## Considered Options

- Redis에 `userId → instanceId` 세션 정보 저장 후 인스턴스 간 전달
- Sticky Session (L7 로드밸런서 기반 세션 고정)
- Kafka 토픽으로 모든 인스턴스에 이벤트 브로드캐스트

## Decision Outcome

Chosen option: **"Redis에 userId → instanceId 세션 정보 저장"**, because 인스턴스가 추가·제거되어도 Redis의 세션 맵을 통해 정확한 인스턴스로 라우팅할 수 있고, Sticky Session처럼 특정 인스턴스 의존성이 없어 장애 복원력이 높기 때문.

```
사용자 A ─WebSocket─ Instance 1
사용자 B ─WebSocket─ Instance 2

Redis: { userId:A → instance:1, userId:B → instance:2 }

Kafka 알림 이벤트 (사용자 A 대상)
      ↓
  Instance 2 소비
      ↓
  Redis 조회 → "instance:1이 담당"
      ↓
  Instance 1에 내부 전달 → 사용자 A WebSocket으로 전송
```

### Consequences

- Good, because 스케일아웃 시에도 정확한 세션으로 알림 전달 가능
- Good, because 인스턴스 장애 시 해당 인스턴스의 세션 정보만 만료(TTL)시키면 됨
- Bad, because Redis 가용성이 WebSocket 라우팅에 직접 영향을 미침 (Redis 장애 = 알림 불가)
- Bad, because 인스턴스 간 내부 통신 메커니즘 추가 구현 필요

### Confirmation

M6 구현 완료 후 확인 항목:
- 다수 Notification Service 인스턴스 기동 상태에서 올바른 인스턴스로 WebSocket 메시지 전달
- 인스턴스 재시작 시 Redis 세션 만료 처리 동작

## Pros and Cons of the Options

### Redis에 `userId → instanceId` 세션 정보 저장

- Good, because 세션 정보가 외부에 있어 인스턴스 무관 라우팅 가능
- Good, because TTL 설정으로 세션 만료를 자동 관리
- Bad, because Redis 장애 시 WebSocket 라우팅 전체 불가
- Bad, because 인스턴스 간 메시지 전달 로직을 직접 구현해야 함

### Sticky Session (L7 로드밸런서 기반 세션 고정)

- Good, because 인프라 레벨에서 처리되므로 애플리케이션 코드 변경 불필요
- Bad, because 특정 인스턴스 장애 시 해당 인스턴스의 모든 세션이 끊어짐
- Bad, because 인스턴스 추가 시 기존 세션 재분배가 되지 않아 부하 불균형 발생

### Kafka 토픽으로 모든 인스턴스에 이벤트 브로드캐스트

- Good, because 세션 라우팅 로직 불필요 — 모든 인스턴스가 이벤트를 받아 자신의 세션 여부만 확인
- Good, because Redis 의존성 없음
- Bad, because 모든 인스턴스가 모든 이벤트를 소비해 인스턴스 수에 비례한 중복 처리 발생
- Bad, because 인스턴스 수 증가 시 Kafka 소비 부하가 선형으로 증가
