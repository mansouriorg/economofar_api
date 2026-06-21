package com.economofar.api.dtos;

public class PasswordUpdateResponse {

    private String message;
    private boolean requiresReLogin;

    public PasswordUpdateResponse(String message, boolean requiresReLogin) {
        this.message = message;
        this.requiresReLogin = requiresReLogin;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRequiresReLogin() {
        return requiresReLogin;
    }
}