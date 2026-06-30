package com.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Banking Application.
 *
 * Annotations explained:
 *  @SpringBootApplication  — combines @Configuration, @EnableAutoConfiguration, and @ComponentScan
 *  @EnableJpaAuditing      — activates automatic population of @CreatedDate / @LastModifiedDate
 *                            fields in BaseEntity (every record gets timestamps for free)
 *  @EnableCaching          — turns on Spring's annotation-driven cache abstraction (@Cacheable, etc.)
 *  @EnableAsync            — allows methods annotated with @Async to run in a background thread pool
 *                            (used by EmailService, AuditService, NotificationService)
 *  @EnableScheduling       — activates @Scheduled methods such as the daily overdue-loan checker
 *                            and the FD maturity processor in LoanService / FixedDepositService
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class BankingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
