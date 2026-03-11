package com.minipay.wallet.dto;

import com.minipay.wallet.entity.Wallet;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class WalletDtos {

    public record CreateWalletRequest(
            @NotNull(message = "Wallet type is required")
            Wallet.WalletType type,

            Wallet.CurrencyCode currency
    ) {}

    public record WalletResponse(
            UUID id,
            UUID userId,
            Wallet.WalletType type,
            Wallet.CurrencyCode currency,
            BigDecimal balance,
            Wallet.WalletStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static WalletResponse from(Wallet wallet) {
            return new WalletResponse(
                    wallet.getId(),
                    wallet.getUserId(),
                    wallet.getType(),
                    wallet.getCurrency(),
                    wallet.getBalance(),
                    wallet.getStatus(),
                    wallet.getCreatedAt(),
                    wallet.getUpdatedAt()
            );
        }
    }

    public record UserRegisteredEvent(String userId, String email, String role) {}
}
