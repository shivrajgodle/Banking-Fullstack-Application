package com.banking.repository;
import com.banking.entity.Account;
import com.banking.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUserId(UUID userId);

    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(UUID userId);

    long countByUserId(UUID userId);
}
