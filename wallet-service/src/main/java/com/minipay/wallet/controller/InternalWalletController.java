package com.minipay.wallet.controller;

import com.minipay.common.dto.ApiResponse;
import com.minipay.wallet.dto.WalletDtos;
import com.minipay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    @PostMapping("/{id}/credit")
    public ResponseEntity<ApiResponse<WalletDtos.WalletTransactionResponse>> credit(
            @PathVariable("id") UUID id,
            @Valid @RequestBody WalletDtos.CreditDebitRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.credit(id, request)));
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<ApiResponse<WalletDtos.WalletTransactionResponse>> debit(
            @PathVariable("id") UUID id,
            @Valid @RequestBody WalletDtos.CreditDebitRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.debit(id, request)));
    }
}
