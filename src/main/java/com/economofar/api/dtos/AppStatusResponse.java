package com.economofar.api.dtos;

import java.time.LocalDateTime;

public class AppStatusResponse {

    private String status;
    private String api;
    private String database;
    private String message;
    private LocalDateTime checkedAt;

    public AppStatusResponse(
            String status,
            String api,
            String database,
            String message,
            LocalDateTime checkedAt
    ) {
        this.status = status;
        this.api = api;
        this.database = database;
        this.message = message;
        this.checkedAt = checkedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getApi() {
        return api;
    }

    public String getDatabase() {
        return database;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }
}