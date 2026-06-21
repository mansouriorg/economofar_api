package com.economofar.api.dtos;

import com.economofar.api.models.User;

import java.util.UUID;

public class UserProfileResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String companyName;
    private String preferredCurrency;
    private String language;
    private boolean requiresReLogin;

    public static UserProfileResponse fromUser(User user) {
        return fromUser(user, false);
    }

    public static UserProfileResponse fromUser(User user, boolean requiresReLogin) {
        UserProfileResponse response = new UserProfileResponse();

        response.id = user.getId();
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.email = user.getEmail();
        response.role = user.getRole() != null ? user.getRole().name() : null;
        response.companyName = user.getCompanyName();
        response.preferredCurrency = user.getPreferredCurrency();
        response.language = user.getLanguage();
        response.requiresReLogin = requiresReLogin;

        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isRequiresReLogin() {
        return requiresReLogin;
    }
}