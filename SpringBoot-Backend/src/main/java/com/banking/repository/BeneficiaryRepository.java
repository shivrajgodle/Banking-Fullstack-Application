package com.banking.repository;
import com.banking.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByUserIdAndStatus(UUID userId, String status);
    Optional<Beneficiary> findByUserIdAndAccountNumber(UUID userId, String accountNumber);
    boolean existsByUserIdAndAccountNumber(UUID userId, String accountNumber);
}
