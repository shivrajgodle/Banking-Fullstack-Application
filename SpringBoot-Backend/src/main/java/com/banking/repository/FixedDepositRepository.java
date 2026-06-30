package com.banking.repository;
import com.banking.entity.FixedDeposit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FixedDepositRepository extends JpaRepository<FixedDeposit, UUID> {
    Optional<FixedDeposit> findByFdNumber(String fdNumber);
    Page<FixedDeposit> findByUserId(UUID userId, Pageable pageable);
    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.status = 'ACTIVE' AND fd.maturityDate <= :today")
    List<FixedDeposit> findMaturingFDs(LocalDate today);
}
