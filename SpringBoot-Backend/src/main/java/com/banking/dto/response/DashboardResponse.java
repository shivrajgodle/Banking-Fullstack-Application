package com.banking.dto.response;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardResponse {
    private BigDecimal totalBalance;
    private int totalAccounts;
    private int activeLoans;
    private int activeFds;
    private BigDecimal totalLoanOutstanding;
    private BigDecimal totalFdAmount;
    private List<TransactionResponse> recentTransactions;
    private List<AccountResponse> accounts;
}
