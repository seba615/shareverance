package com.shareverance.model;

import java.time.LocalDateTime;

public class UserOrganization {
    private Long id;
    private Long userId;
    private Long organizationId;
    private MembershipRole role;
    private LocalDateTime joinedAt;
    private Boolean isActive;
    private String userName;
    private String userEmail;

    public UserOrganization() {}

    public UserOrganization(Long id, Long userId, Long organizationId, MembershipRole role, LocalDateTime joinedAt, Boolean isActive) {
        this(id, userId, organizationId, role, joinedAt, isActive, null, null);
    }

    public UserOrganization(Long id, Long userId, Long organizationId, MembershipRole role, LocalDateTime joinedAt, Boolean isActive, String userName, String userEmail) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.isActive = isActive;
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public MembershipRole getRole() { return role; }
    public void setRole(MembershipRole role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
}

