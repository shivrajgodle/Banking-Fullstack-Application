package com.banking.controller;

import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.AuditLog;
import com.banking.repository.AccountRepository;
import com.banking.repository.AuditLogRepository;
import com.banking.repository.LoanRepository;
import com.banking.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only system operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final LoanRepository loanRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    @Operation(summary = "Get system-wide statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long pendingLoans = loanRepository.findAll().stream()
                .filter(l -> l.getStatus().name().equals("PENDING")).count();
        Map<String, Object> stats = Map.of(
                "totalUsers", userRepository.count(),
                "totalAccounts", accountRepository.count(),
                "totalLoans", loanRepository.count(),
                "pendingLoans", pendingLoans
        );
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get all audit logs (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> logsPage = auditLogRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        PageResponse<AuditLog> response = PageResponse.<AuditLog>builder()
                .content(logsPage.getContent())
                .page(page).size(size)
                .totalElements(logsPage.getTotalElements())
                .totalPages(logsPage.getTotalPages())
                .first(logsPage.isFirst()).last(logsPage.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/audit-logs/user/{userId}")
    @Operation(summary = "Get audit logs for a specific user")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getUserAuditLogs(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> logsPage = auditLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        PageResponse<AuditLog> response = PageResponse.<AuditLog>builder()
                .content(logsPage.getContent())
                .page(page).size(size)
                .totalElements(logsPage.getTotalElements())
                .totalPages(logsPage.getTotalPages())
                .first(logsPage.isFirst()).last(logsPage.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
