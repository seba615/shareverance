package com.shareverance.model;

import java.time.LocalDateTime;

public class Category {
    private Long id;
    private Long organizationId;
    private String name;
    private String description;
    private String color;
    private LocalDateTime createdAt;
    private Boolean isActive;

    public Category() {}

    public Category(Long id, Long organizationId, String name, String description, String color, LocalDateTime createdAt, Boolean isActive) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.color = color;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}

