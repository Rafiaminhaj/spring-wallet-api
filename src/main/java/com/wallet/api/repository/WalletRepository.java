package com.wallet.api.repository;

import com.wallet.api.entity.User;
import com.wallet.api.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    Optional<Wallet> findByUser(User user);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the wallet row
     * to prevent concurrent double-spending operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
