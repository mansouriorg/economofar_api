package com.economofar.api.dtos;

public class UpdateUserProfileRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String companyName;
    private String preferredCurrency;
    private String language;

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
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

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = preferredCurrency;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}