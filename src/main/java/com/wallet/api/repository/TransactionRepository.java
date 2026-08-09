package com.wallet.api.repository;

import com.wallet.api.entity.Transaction;
import com.wallet.api.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    /**
     * Finds all transaction logs where the wallet was either the sender (source) or receiver (target),
     * ordered from newest to oldest.
     */
    List<Transaction> findBySourceWalletOrTargetWalletOrderByTimestampDesc(Wallet source, Wallet target);
}
