package com.minipay.wallet.repository;

import com.minipay.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByUserId(UUID userId);
    Optional<Wallet> findByUserIdAndTypeAndCurrency(UUID userId, Wallet.WalletType type, Wallet.CurrencyCode currency);
    boolean existsByUserIdAndTypeAndCurrency(UUID userId, Wallet.WalletType type, Wallet.CurrencyCode currency);
}
