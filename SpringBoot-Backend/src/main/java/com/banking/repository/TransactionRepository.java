package com.banking.repository;
import com.banking.entity.Transaction;
import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByTransactionRef(String transactionRef);
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    Page<Transaction> findByAccountIdAndTransactionTypeOrderByCreatedAtDesc(UUID accountId, TransactionType type, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.account.id = :accountId " +
           "AND t.transactionType IN ('TRANSFER_OUT', 'WITHDRAWAL') " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt >= :startOfDay")
    BigDecimal sumDailyOutflow(UUID accountId, LocalDateTime startOfDay);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
           "AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountIdAndDateRange(UUID accountId, LocalDateTime from, LocalDateTime to);

    List<Transaction> findByAccountIdAndStatus(UUID accountId, TransactionStatus status);
}
