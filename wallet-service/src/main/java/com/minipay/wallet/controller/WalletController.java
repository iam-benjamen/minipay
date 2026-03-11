package com.minipay.wallet.controller;

import com.minipay.common.dto.ApiResponse;
import com.minipay.wallet.dto.WalletDtos;
import com.minipay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WalletDtos.WalletResponse>>> listWallets(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWallets(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WalletDtos.WalletResponse>> getWallet(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWallet(id, userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletDtos.WalletResponse>> createWallet(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody WalletDtos.CreateWalletRequest request
    ) {
        WalletDtos.WalletResponse response = walletService.createWallet(userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
