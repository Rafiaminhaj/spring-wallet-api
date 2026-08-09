package com.wallet.api;

import com.wallet.api.entity.User;
import com.wallet.api.entity.Wallet;
import com.wallet.api.repository.UserRepository;
import com.wallet.api.repository.WalletRepository;
import com.wallet.api.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class WalletApplicationTests {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void testConcurrentTransfersWithPessimisticLocking() throws InterruptedException {
        // 1. Create two users (Standard constructor calls)
        User alice = new User("alice_test", "pass", "ROLE_USER");
        User bob = new User("bob_test", "pass", "ROLE_USER");

        userRepository.save(alice);
        userRepository.save(bob);

        // 2. Initialize wallets with default balance ($1000 each)
        Wallet aliceWallet = walletService.getWalletByUser(alice);
        Wallet bobWallet = walletService.getWalletByUser(bob);

        assertEquals(new BigDecimal("1000.00"), aliceWallet.getBalance());
        assertEquals(new BigDecimal("1000.00"), bobWallet.getBalance());

        // 3. Fire 15 concurrent threads trying to transfer $100 each from Alice to Bob (Total requested = $1500)
        // Since Alice only has $1000, exactly 10 transfers must succeed, and 5 must fail.
        int totalThreads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalThreads);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        BigDecimal transferAmount = new BigDecimal("100.00");

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // Wait for start signal to execute simultaneously
                    walletService.transferMoney(aliceWallet.getId(), bobWallet.getId(), transferAmount, "Concurrent Transfer");
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    failureCounter.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Start all threads simultaneously
        latch.countDown();
        doneSignal.await(); // Wait for all threads to finish execution
        executor.shutdown();

        // 4. Assert correctness
        // Exactly 10 transfers must have succeeded
        assertEquals(10, successCounter.get(), "Exactly 10 transfers should succeed");
        // Exactly 5 transfers must have failed due to insufficient funds
        assertEquals(5, failureCounter.get(), "Exactly 5 transfers should fail");

        // Fetch fresh state from db
        Wallet updatedAliceWallet = walletRepository.findById(aliceWallet.getId()).orElseThrow();
        Wallet updatedBobWallet = walletRepository.findById(bobWallet.getId()).orElseThrow();

        // Alice should have exactly $0.00 left ($1000 - 10 * $100)
        assertEquals(0, updatedAliceWallet.getBalance().compareTo(BigDecimal.ZERO), "Alice balance should be exactly 0");
        // Bob should have exactly $2000.00 ($1000 + 10 * $100)
        assertEquals(0, updatedBobWallet.getBalance().compareTo(new BigDecimal("2000.00")), "Bob balance should be exactly 2000");
    }
}
