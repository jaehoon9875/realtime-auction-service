package com.jaehoon.bid.entity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입찰 저장과 동일 트랜잭션에서 함께 저장되어 Debezium이 Kafka로 전달한다.
 * docs/kafka.md 기준 bid-events의 eventType(BID_PLACED/BID_REJECTED)을 담는 저장소다.
 */
@Entity
@Table(name = "outbox_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_key", nullable = false)
    private UUID eventKey;

    @Column(name = "event_type", nullable = false, length = 50)
    // 예: BID_PLACED, BID_REJECTED
    private String eventType;

    @Column(nullable = false, columnDefinition = "bytea")
    // Confluent Avro wire format — Debezium BinaryDataConverter가 Kafka로 그대로 전달한다.
    private byte[] payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public OutboxEvent(String aggregateType, UUID aggregateId, UUID eventKey, String eventType, byte[] payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.payload = payload;
    }
}
