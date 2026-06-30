package com.banking.repository;
import com.banking.entity.Otp;
import com.banking.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<Otp, UUID> {
    Optional<Otp> findByUserIdAndOtpTypeAndUsedFalseAndExpiresAtAfter(UUID userId, OtpType type, LocalDateTime now);
    @Modifying
    @Query("UPDATE Otp o SET o.used = true WHERE o.user.id = :userId AND o.otpType = :type AND o.used = false")
    void invalidateOtps(UUID userId, OtpType type);
}
