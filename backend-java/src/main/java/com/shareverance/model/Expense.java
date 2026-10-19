package com.shareverance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Expense {
    private Long id;
    private Long organizationId;
    private Long createdByUserId;
    private Long paidByMembershipId;
    private Long categoryId;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private String receiptUrl;
    private String splitType = "EQUAL";
    private LocalDateTime createdAt;
    private Boolean isActive;
    private Integer version = 1;

    public Expense() {}

    public Expense(Long id, Long organizationId, Long createdByUserId, Long paidByMembershipId, Long categoryId,
                   BigDecimal amount, LocalDate expenseDate, String description, String receiptUrl,
                   LocalDateTime createdAt, Boolean isActive) {
        this(id, organizationId, createdByUserId, paidByMembershipId, categoryId, amount, expenseDate,
                description, receiptUrl, createdAt, isActive, 1);
    }

    public Expense(Long id, Long organizationId, Long createdByUserId, Long paidByMembershipId, Long categoryId,
                   BigDecimal amount, LocalDate expenseDate, String description, String receiptUrl,
                   LocalDateTime createdAt, Boolean isActive, Integer version) {
        this.id = id;
        this.organizationId = organizationId;
        this.createdByUserId = createdByUserId;
        this.paidByMembershipId = paidByMembershipId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.description = description;
        this.receiptUrl = receiptUrl;
        this.createdAt = createdAt;
        this.isActive = isActive;
        this.version = version;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }

    public Long getPaidByMembershipId() { return paidByMembershipId; }
    public void setPaidByMembershipId(Long paidByMembershipId) { this.paidByMembershipId = paidByMembershipId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }

    public String getSplitType() { return splitType; }
    public void setSplitType(String splitType) { this.splitType = splitType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
