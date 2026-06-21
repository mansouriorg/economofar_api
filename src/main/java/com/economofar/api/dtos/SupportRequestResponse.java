package com.economofar.api.dtos;

import com.economofar.api.models.SupportRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class SupportRequestResponse {

    private UUID id;
    private String type;
    private String status;
    private String subject;
    private String message;
    private String appVersion;
    private String deviceModel;
    private String osVersion;
    private String contactEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SupportRequestResponse fromEntity(SupportRequest request) {
        SupportRequestResponse response = new SupportRequestResponse();
        response.id = request.getId();
        response.type = request.getType().name();
        response.status = request.getStatus().name();
        response.subject = request.getSubject();
        response.message = request.getMessage();
        response.appVersion = request.getAppVersion();
        response.deviceModel = request.getDeviceModel();
        response.osVersion = request.getOsVersion();
        response.contactEmail = request.getContactEmail();
        response.createdAt = request.getCreatedAt();
        response.updatedAt = request.getUpdatedAt();
        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
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
}