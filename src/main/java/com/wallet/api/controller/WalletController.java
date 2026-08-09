package com.wallet.api.controller;

import com.wallet.api.entity.User;
import com.wallet.api.entity.Wallet;
import com.wallet.api.entity.Transaction;
import com.wallet.api.repository.UserRepository;
import com.wallet.api.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    // Standard constructor for dependency injection
    public WalletController(WalletService walletService, UserRepository userRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    @GetMapping("/balance")
    public ResponseEntity<Wallet> getBalance(@AuthenticationPrincipal User currentUser) {
        Wallet wallet = walletService.getWalletByUser(currentUser);
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@AuthenticationPrincipal User currentUser,
                                      @Valid @RequestBody TransferRequest request) {
        
        Wallet sourceWallet = walletService.getWalletByUser(currentUser);
        
        // Fetch target user context
        User targetUser = userRepository.findByUsername(request.getTargetUsername())
                .orElse(null);
        if (targetUser == null) {
            return ResponseEntity.badRequest().body("Error: Target user '" + request.getTargetUsername() + "' does not exist.");
        }
        
        Wallet targetWallet = walletService.getWalletByUser(targetUser);

        try {
            walletService.transferMoney(
                    sourceWallet.getId(),
                    targetWallet.getId(),
                    request.getAmount(),
                    request.getDescription()
            );
            return ResponseEntity.ok("Success: Transfer of $" + request.getAmount() + " to '" + request.getTargetUsername() + "' completed successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: Transfer failed: " + e.getMessage());
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@AuthenticationPrincipal User currentUser) {
        List<Transaction> history = walletService.getTransactionHistory(currentUser);
        return ResponseEntity.ok(history);
    }

    // Standard Java DTO
    public static class TransferRequest {
        @NotBlank(message = "Target username is required")
        private String targetUsername;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        private BigDecimal amount;

        private String description;

        public TransferRequest() {}

        public TransferRequest(String targetUsername, BigDecimal amount, String description) {
            this.targetUsername = targetUsername;
            this.amount = amount;
            this.description = description;
        }

        public String getTargetUsername() {
            return targetUsername;
        }

        public void setTargetUsername(String targetUsername) {
            this.targetUsername = targetUsername;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
