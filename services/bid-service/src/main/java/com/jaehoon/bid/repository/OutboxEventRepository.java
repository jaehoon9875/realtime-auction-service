package com.jaehoon.bid.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jaehoon.bid.entity.OutboxEvent;

/** Outbox 적재 전용 저장소. Kafka 직접 발행 대신 DB 트랜잭션으로 이벤트를 기록한다. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
