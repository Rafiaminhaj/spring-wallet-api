# 💳 Spring-Wallet-API: Secured FinTech Ledger & Digital Wallet REST API

Spring-Wallet-API is a high-performance, secure backend financial ledger service built with **Spring Boot 3.3**, **Java 21**, **Spring Security 6 (JWT)**, and **Spring Data JPA (H2 In-Memory Database)**. It provides secure user wallets, transactional balances, and ledger logs while implementing advanced concurrency controls to eliminate double-spending and database deadlocks.

---

## 🚀 Key Features

*   **Stateless JWT Authentication**: Implements JSON Web Tokens for secure session authentication. Users register and receive a secure token to call wallet endpoints.
*   **Pessimistic Locking Concurrency Control**: Uses database-level `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) to prevent race conditions during high-volume concurrent wallet transactions.
*   **Deadlock Prevention Algorithm**: Dynamically sorts wallet resource acquisition (by ascending primary key IDs) before locking to eliminate circular lock dependencies (deadlocks).
*   **ACID Transactional Ledger**: Every transfer updates balance logs atomicaly using Spring's `@Transactional` rollback features. If any error occurs, the transfer is rolled back cleanly.
*   **Zero-Dependency Setup**: Configured with H2 in-memory database to allow running the app out-of-the-box on any machine.
*   **Bruno Collection Ready**: Includes a dedicated Bruno folder (`bruno/`) containing pre-configured request files for easy API testing.

---

## 🛠️ Technology Stack

*   **Language**: Java 21 (LTS)
*   **Backend Framework**: Spring Boot 3.3.0
*   **Security Framework**: Spring Security 6 + io.jsonwebtoken (0.12.5)
*   **Data Access**: Spring Data JPA / Hibernate ORM
*   **Database**: H2 In-Memory DB
*   **Build Tool**: Maven 3.9+
*   **Boilerplate Control**: Project Lombok

---

## 📂 Core Package Structure

```text
com.wallet.api/
├── WalletApplication.java      # Main entry point
├── entity/
│   ├── User.java               # Auth user model
│   ├── Wallet.java             # Holds currency balance (BigDecimal)
│   └── Transaction.java        # Audit trail transfer ledger
├── repository/
│   ├── UserRepository.java
│   ├── WalletRepository.java   # Custom Pessimistic Locking query
│   └── TransactionRepository.java
├── security/
│   ├── JwtUtil.java            # JWT generator and parser
│   ├── JwtFilter.java          # JWT request validator filter
│   └── SecurityConfig.java     # Route authentication configurations
├── service/
│   └── WalletService.java      # Transactional business logic & deadlock avoidance
└── controller/
    ├── AuthController.java     # Sign-up & Sign-in endpoints
    └── WalletController.java   # Transfer, balance & transactions endpoints
```

---

## 💡 Engineering Highlights: Concurrency & Lock Sorting

### 1. The Double-Spending Problem
In financial APIs, if Alice has $100 and sends $80 to Bob and $80 to Charlie at the exact same millisecond, a standard database write without proper isolation could let both transfers succeed (balance becomes -$60).
To resolve this, we use a **Pessimistic Write Lock**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
```
This forces the database to block other threads from reading or editing Alice's row until the transaction commits.

### 2. Deadlock Avoidance
If Thread A wants to transfer from Wallet 1 to Wallet 2 (locks Wallet 1, waits for 2), and Thread B wants to transfer from Wallet 2 to Wallet 1 (locks Wallet 2, waits for 1), they wait for each other infinitely (Deadlock).
We solve this by **sorting the lock acquisition order**:
```java
Long lockFirst = Math.min(sourceWalletId, targetWalletId);
Long lockSecond = Math.max(sourceWalletId, targetWalletId);

// Acquire locks in strict ID order - deadlock is mathematically impossible!
Wallet walletFirst = walletRepository.findByIdForUpdate(lockFirst).orElseThrow(...);
Wallet walletSecond = walletRepository.findByIdForUpdate(lockSecond).orElseThrow(...);
```

---

## ⚙️ How to Build and Run

### 1. Pre-requisites
*   Java JDK 21+ installed.
*   Maven installed.

### 2. Run the application
Run the Maven command in the root folder:
```bash
mvn spring-boot:run
```
The server will boot up on port `8080` (e.g. `http://localhost:8080`).

### 3. Open H2 Database Console
You can view the tables in your web browser:
*   URL: `http://localhost:8080/h2-console`
*   JDBC URL: `jdbc:h2:mem:walletdb`
*   Username: `sa`
*   Password: *(leave blank)*

---

## 🧪 Testing the API (Bruno Client)
1. Open the [Bruno API Client](https://www.usebruno.com/).
2. Click **Open Collection** and choose the `bruno/` directory in this project.
3. Execute the requests in sequence:
   *   **Register**: Create user accounts (`alice` and `bob`).
   *   **Login**: Login as `alice` and copy the returned `token`.
   *   **Get Balance**: Paste token into the auth configuration of headers to check balance.
   *   **Transfer Money**: Transfer money to `bob` and verify audit history logs.
