package com.wallet.api.service;

import com.wallet.api.entity.Transaction;
import com.wallet.api.entity.User;
import com.wallet.api.entity.Wallet;
import com.wallet.api.repository.TransactionRepository;
import com.wallet.api.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // Standard constructor for dependency injection
    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Gets the wallet linked to a specific user. 
     * If the user doesn't have a wallet, initializes one with a default balance of $1000.00.
     */
    public Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseGet(() -> {
                    Wallet newWallet = new Wallet(user, new BigDecimal("1000.00"));
                    return walletRepository.save(newWallet);
                });
    }

    /**
     * Safely transfers money between two wallets with absolute transactional integrity.
     * Implements database Pessimistic Write Locking in a sorted order to avoid deadlock conditions.
     */
    @Transactional
    public void transferMoney(Long sourceWalletId, Long targetWalletId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }
        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException("Source and target wallets must be different.");
        }

        // To prevent DEADLOCKS in concurrent database locks:
        // Always acquire locks in the sorted order of Wallet IDs!
        Long lockFirst = Math.min(sourceWalletId, targetWalletId);
        Long lockSecond = Math.max(sourceWalletId, targetWalletId);

        Wallet walletFirst = walletRepository.findByIdForUpdate(lockFirst)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: ID " + lockFirst));
        Wallet walletSecond = walletRepository.findByIdForUpdate(lockSecond)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: ID " + lockSecond));

        // Re-assign pointers to check balances correctly
        Wallet sourceWallet = (sourceWalletId.equals(lockFirst)) ? walletFirst : walletSecond;
        Wallet targetWallet = (targetWalletId.equals(lockFirst)) ? walletFirst : walletSecond;

        // Perform business validation
        if (sourceWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient wallet balance for transfer.");
        }

        // Execute ledger entries (ACID Transfer)
        sourceWallet.setBalance(sourceWallet.getBalance().subtract(amount));
        targetWallet.setBalance(targetWallet.getBalance().add(amount));

        walletRepository.save(sourceWallet);
        walletRepository.save(targetWallet);

        // Record audit trial log (Standard Java Constructor call)
        Transaction transaction = new Transaction(
                sourceWallet,
                targetWallet,
                amount,
                LocalDateTime.now(),
                description != null ? description : "Wallet Transfer"
        );

        transactionRepository.save(transaction);
    }

    /**
     * Fetches transaction ledger history (sent/received transfers) for a specific user.
     */
    public List<Transaction> getTransactionHistory(User user) {
        Wallet wallet = getWalletByUser(user);
        return transactionRepository.findBySourceWalletOrTargetWalletOrderByTimestampDesc(wallet, wallet);
    }
}
