package com.minipay.wallet.repository;

import com.minipay.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByUserId(UUID userId);
    Optional<Wallet> findByUserIdAndTypeAndCurrency(UUID userId, Wallet.WalletType type, Wallet.CurrencyCode currency);
    boolean existsByUserIdAndTypeAndCurrency(UUID userId, Wallet.WalletType type, Wallet.CurrencyCode currency);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount WHERE w.id = :walletId AND w.status = :status")
    int credit(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount, @Param("status") Wallet.WalletStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount WHERE w.id = :walletId AND w.status = :status AND w.balance >= :amount")
    int debit(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount, @Param("status") Wallet.WalletStatus status);
}
