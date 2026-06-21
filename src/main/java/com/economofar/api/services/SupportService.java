package com.economofar.api.services;

import com.economofar.api.dtos.*;
import com.economofar.api.models.SupportRequest;
import com.economofar.api.models.SupportRequestType;
import com.economofar.api.models.User;
import com.economofar.api.repositories.FaqItemRepository;
import com.economofar.api.repositories.SupportRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportService {

    private final FaqItemRepository faqItemRepository;
    private final SupportRequestRepository supportRequestRepository;

    @Value("${economofar.support.email:support@economofar.com}")
    private String supportEmail;

    @Value("${economofar.support.terms-url:https://economofar.com/terms}")
    private String termsUrl;

    @Value("${economofar.support.privacy-url:https://economofar.com/privacy}")
    private String privacyUrl;

    @Value("${economofar.support.status-url:https://status.economofar.com}")
    private String statusPageUrl;

    public SupportService(
            FaqItemRepository faqItemRepository,
            SupportRequestRepository supportRequestRepository
    ) {
        this.faqItemRepository = faqItemRepository;
        this.supportRequestRepository = supportRequestRepository;
    }

    public List<FaqItemResponse> getFaqs(String category) {
        if (category != null && !category.isBlank()) {
            return faqItemRepository
                    .findByActiveTrueAndCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtAsc(category)
                    .stream()
                    .map(FaqItemResponse::fromEntity)
                    .toList();
        }

        return faqItemRepository
                .findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()
                .stream()
                .map(FaqItemResponse::fromEntity)
                .toList();
    }

    public SupportRequestResponse submitContactSupport(User user, SubmitSupportRequest request) {
        SupportRequest supportRequest = createRequest(
                user,
                SupportRequestType.CONTACT_SUPPORT,
                request
        );

        return SupportRequestResponse.fromEntity(
                supportRequestRepository.save(supportRequest)
        );
    }

    public SupportRequestResponse reportBug(User user, SubmitSupportRequest request) {
        SupportRequest supportRequest = createRequest(
                user,
                SupportRequestType.BUG_REPORT,
                request
        );

        return SupportRequestResponse.fromEntity(
                supportRequestRepository.save(supportRequest)
        );
    }

    public List<SupportRequestResponse> getMyRequests(User user) {
        return supportRequestRepository
                .findTop50ByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(SupportRequestResponse::fromEntity)
                .toList();
    }

    public SupportConfigResponse getSupportConfig() {
        return new SupportConfigResponse(
                supportEmail,
                termsUrl,
                privacyUrl,
                statusPageUrl
        );
    }

    public AppStatusResponse getAppStatus() {
        try {
            faqItemRepository.count();

            return new AppStatusResponse(
                    "OPERATIONAL",
                    "UP",
                    "UP",
                    "Tous les services sont opérationnels.",
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            return new AppStatusResponse(
                    "DEGRADED",
                    "UP",
                    "DOWN",
                    "L’API fonctionne, mais la base de données ne répond pas correctement.",
                    LocalDateTime.now()
            );
        }
    }

    private SupportRequest createRequest(
            User user,
            SupportRequestType type,
            SubmitSupportRequest request
    ) {
        validateRequest(request);

        String contactEmail = request.getContactEmail();

        if ((contactEmail == null || contactEmail.isBlank()) && user != null) {
            contactEmail = user.getEmail();
        }

        return new SupportRequest(
                user,
                type,
                request.getSubject().trim(),
                request.getMessage().trim(),
                clean(request.getAppVersion()),
                clean(request.getDeviceModel()),
                clean(request.getOsVersion()),
                clean(contactEmail)
        );
    }

    private void validateRequest(SubmitSupportRequest request) {
        if (request == null) {
            throw new RuntimeException("Request body is required");
        }

        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new RuntimeException("Subject is required");
        }

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new RuntimeException("Message is required");
        }

        if (request.getSubject().length() > 200) {
            throw new RuntimeException("Subject is too long");
        }

        if (request.getMessage().length() > 5000) {
            throw new RuntimeException("Message is too long");
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}