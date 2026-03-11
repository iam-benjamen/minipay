package com.minipay.wallet.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.wallet.dto.WalletDtos;
import com.minipay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletConsumer {

    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "minipay.user.registered")
    public void onUserRegistered(String payload) {
        try {
            WalletDtos.UserRegisteredEvent event = objectMapper.readValue(payload, WalletDtos.UserRegisteredEvent.class);
            walletService.createDefaultWallet(UUID.fromString(event.userId()), event.role());
        } catch (Exception e) {
            log.error("Failed to process user.registered event: {}", e.getMessage());
        }
    }
}
