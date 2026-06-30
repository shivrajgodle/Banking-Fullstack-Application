package com.banking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract base class inherited by every JPA entity in the application.
 *
 * Provides three common fields that every database table needs:
 *
 *  id (UUID)
 *    - Uses a database-level UUID generation strategy (GenerationType.UUID),
 *      which means the DB generates the ID at INSERT time.
 *    - UUIDs are used instead of auto-increment integers to:
 *        a) Avoid sequential IDs that reveal record counts to clients
 *        b) Support distributed systems / microservices without ID collisions
 *    - updatable=false ensures the ID can never accidentally be overwritten
 *
 *  createdAt
 *    - Automatically populated by Spring Data JPA's @CreatedDate when the
 *      entity is first persisted (INSERT). updatable=false prevents it from
 *      changing on subsequent UPDATEs.
 *    - Requires @EnableJpaAuditing on BankingApplication + @EntityListeners here.
 *
 *  updatedAt
 *    - Automatically updated by @LastModifiedDate on every save (INSERT or UPDATE).
 *    - Useful for cache invalidation, optimistic locking hints, and audit trails.
 *
 * @MappedSuperclass tells JPA to include these fields in every subclass table
 * rather than creating a separate "base_entity" table.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // activates @CreatedDate / @LastModifiedDate auto-fill
public abstract class BaseEntity {

    /** Unique identifier for every record — generated at the database level. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Timestamp set once when the record is first created; never updated afterwards. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp automatically updated on every save/update operation. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
