package com.minipay.wallet.service;

import com.minipay.common.exception.ConflictException;
import com.minipay.common.exception.ResourceNotFoundException;
import com.minipay.common.exception.UnauthorizedException;
import com.minipay.wallet.dto.WalletDtos;
import com.minipay.wallet.entity.Wallet;
import com.minipay.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public void createDefaultWallet(UUID userId, String role) {
        Wallet.WalletType type = switch (role) {
            case "CUSTOMER" -> Wallet.WalletType.PERSONAL;
            case "MERCHANT" -> Wallet.WalletType.BUSINESS;
            default -> {
                log.info("No default wallet created for role {} (userId={})", role, userId);
                yield null;
            }
        };

        if (type == null) return;

        Wallet.CurrencyCode currency = Wallet.CurrencyCode.NGN;
        if (walletRepository.existsByUserIdAndTypeAndCurrency(userId, type, currency)) {
            log.info("Default wallet already exists for userId={}", userId);
            return;
        }

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .type(type)
                .currency(currency)
                .build();
        walletRepository.save(wallet);
        log.info("Created default {} wallet for userId={}", type, userId);
    }

    @Transactional
    public WalletDtos.WalletResponse createWallet(UUID userId, String role, WalletDtos.CreateWalletRequest request) {
        if ("ADMIN".equals(role)) {
            throw new UnauthorizedException("Admins cannot have wallets");
        }

        Wallet.WalletType type = request.type();
        if ("CUSTOMER".equals(role) && type == Wallet.WalletType.BUSINESS) {
            throw new UnauthorizedException("Customers cannot create business wallets");
        }
        if ("MERCHANT".equals(role) && type != Wallet.WalletType.BUSINESS) {
            throw new UnauthorizedException("Merchants can only create business wallets");
        }

        Wallet.CurrencyCode currency = request.currency() != null ? request.currency() : Wallet.CurrencyCode.NGN;
        if (walletRepository.existsByUserIdAndTypeAndCurrency(userId, type, currency)) {
            throw new ConflictException("Wallet of this type and currency already exists");
        }

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .type(type)
                .currency(currency)
                .build();
        return WalletDtos.WalletResponse.from(walletRepository.save(wallet));
    }

    @Transactional(readOnly = true)
    public List<WalletDtos.WalletResponse> getWallets(UUID userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(WalletDtos.WalletResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletDtos.WalletResponse getWallet(UUID walletId, UUID userId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", walletId.toString()));
        if (!wallet.getUserId().equals(userId)) {
            throw new UnauthorizedException("Access denied");
        }
        return WalletDtos.WalletResponse.from(wallet);
    }
}
