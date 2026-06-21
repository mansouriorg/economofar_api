package com.economofar.api.dtos;

public class SubmitSupportRequest {

    private String subject;
    private String message;
    private String appVersion;
    private String deviceModel;
    private String osVersion;
    private String contactEmail;

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

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}