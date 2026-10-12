package com.shareverance.model;

import java.math.BigDecimal;

public class ExpenseSplit {
    private Long id;
    private Long expenseId;
    private Long userOrganizationId;
    private BigDecimal amount;

    public ExpenseSplit() {}

    public ExpenseSplit(Long id, Long expenseId, Long userOrganizationId, BigDecimal amount) {
        this.id = id;
        this.expenseId = expenseId;
        this.userOrganizationId = userOrganizationId;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExpenseId() { return expenseId; }
    public void setExpenseId(Long expenseId) { this.expenseId = expenseId; }

    public Long getUserOrganizationId() { return userOrganizationId; }
    public void setUserOrganizationId(Long userOrganizationId) { this.userOrganizationId = userOrganizationId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

