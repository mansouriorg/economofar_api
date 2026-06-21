package com.economofar.api.dtos;

import com.economofar.api.models.FaqItem;

import java.time.LocalDateTime;
import java.util.UUID;

public class FaqItemResponse {

    private UUID id;
    private String category;
    private String question;
    private String answer;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FaqItemResponse fromEntity(FaqItem faqItem) {
        FaqItemResponse response = new FaqItemResponse();
        response.id = faqItem.getId();
        response.category = faqItem.getCategory();
        response.question = faqItem.getQuestion();
        response.answer = faqItem.getAnswer();
        response.displayOrder = faqItem.getDisplayOrder();
        response.createdAt = faqItem.getCreatedAt();
        response.updatedAt = faqItem.getUpdatedAt();
        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}