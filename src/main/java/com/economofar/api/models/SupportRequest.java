package com.economofar.api.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_requests")
public class SupportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportRequestStatus status = SupportRequestStatus.OPEN;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 5000)
    private String message;

    @Column(length = 100)
    private String appVersion;

    @Column(length = 150)
    private String deviceModel;

    @Column(length = 150)
    private String osVersion;

    @Column(length = 150)
    private String contactEmail;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SupportRequest() {
    }

    public SupportRequest(
            User user,
            SupportRequestType type,
            String subject,
            String message,
            String appVersion,
            String deviceModel,
            String osVersion,
            String contactEmail
    ) {
        this.user = user;
        this.type = type;
        this.subject = subject;
        this.message = message;
        this.appVersion = appVersion;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
        this.contactEmail = contactEmail;
        this.status = SupportRequestStatus.OPEN;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SupportRequestType getType() {
        return type;
    }

    public SupportRequestStatus getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(SupportRequestStatus status) {
        this.status = status;
    }
}