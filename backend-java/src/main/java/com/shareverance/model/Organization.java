package com.shareverance.model;

import java.time.LocalDateTime;

public class Organization {
    private Long id;
    private String name;
    private OrganizationType type;
    private String currency; // default ARS
    private LocalDateTime createdAt;
    private Boolean isActive;

    public Organization() {}

    public Organization(Long id, String name, OrganizationType type, String currency, LocalDateTime createdAt, Boolean isActive) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OrganizationType getType() { return type; }
    public void setType(OrganizationType type) { this.type = type; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}

