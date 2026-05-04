package com.jaehoon.auction.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jaehoon.auction.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
