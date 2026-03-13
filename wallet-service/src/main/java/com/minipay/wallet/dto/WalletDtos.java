package com.minipay.wallet.dto;

import com.minipay.wallet.entity.Wallet;
import com.minipay.wallet.entity.WalletTransaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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

    public record CreditDebitRequest(
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            String reference
    ) {}

    public record WalletTransactionResponse(
            UUID id,
            UUID walletId,
            WalletTransaction.TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String reference,
            LocalDateTime createdAt
    ) {
        public static WalletTransactionResponse from(WalletTransaction tx) {
            return new WalletTransactionResponse(
                    tx.getId(),
                    tx.getWalletId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getBalanceAfter(),
                    tx.getReference(),
                    tx.getCreatedAt()
            );
        }
    }

    public record UserRegisteredEvent(String userId, String email, String role) {}
}
