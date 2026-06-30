package com.banking.repository;
import com.banking.entity.Loan;
import com.banking.enums.LoanStatus;
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
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    Optional<Loan> findByLoanNumber(String loanNumber);
    Page<Loan> findByUserId(UUID userId, Pageable pageable);
    List<Loan> findByUserIdAndStatus(UUID userId, LoanStatus status);
    @Query("SELECT l FROM Loan l WHERE l.status = 'ACTIVE' AND l.nextEmiDate <= :today")
    List<Loan> findLoansWithDueEmi(LocalDate today);
    @Query("SELECT l FROM Loan l WHERE l.status = 'ACTIVE' AND l.nextEmiDate < :today")
    List<Loan> findOverdueLoans(LocalDate today);
}
