package com.economofar.api.dtos;

public class SupportConfigResponse {

    private String supportEmail;
    private String termsUrl;
    private String privacyUrl;
    private String statusPageUrl;

    public SupportConfigResponse(
            String supportEmail,
            String termsUrl,
            String privacyUrl,
            String statusPageUrl
    ) {
        this.supportEmail = supportEmail;
        this.termsUrl = termsUrl;
        this.privacyUrl = privacyUrl;
        this.statusPageUrl = statusPageUrl;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getTermsUrl() {
        return termsUrl;
    }

    public String getPrivacyUrl() {
        return privacyUrl;
    }

    public String getStatusPageUrl() {
        return statusPageUrl;
    }
}