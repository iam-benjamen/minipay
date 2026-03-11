package com.minipay.auth.service;

import com.minipay.auth.entity.OutboxEvent;
import com.minipay.auth.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload()).get();
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to relay outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
