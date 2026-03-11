package com.minipay.auth.repository;

import com.minipay.auth.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc();
}
