package com.economofar.api.services;

import com.economofar.api.dtos.UpdateNotificationPreferencesRequest;
import com.economofar.api.models.AppNotification;
import com.economofar.api.models.NotificationPreference;
import com.economofar.api.models.NotificationType;
import com.economofar.api.models.User;
import com.economofar.api.repositories.AppNotificationRepository;
import com.economofar.api.repositories.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final AppNotificationRepository notificationRepository;

    public NotificationService(
            NotificationPreferenceRepository preferenceRepository,
            AppNotificationRepository notificationRepository
    ) {
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
    }

    public NotificationPreference getOrCreatePreferences(User user) {
        return preferenceRepository
                .findByUser(user)
                .orElseGet(() -> preferenceRepository.save(new NotificationPreference(user)));
    }

    @Transactional
    public NotificationPreference updatePreferences(User user, UpdateNotificationPreferencesRequest request) {
        NotificationPreference preferences = getOrCreatePreferences(user);

        if (request.getTicketAnalysisCompleted() != null) {
            preferences.setTicketAnalysisCompleted(request.getTicketAnalysisCompleted());
        }

        if (request.getMonthlySpendingSummary() != null) {
            preferences.setMonthlySpendingSummary(request.getMonthlySpendingSummary());
        }

        if (request.getUnusualSpending() != null) {
            preferences.setUnusualSpending(request.getUnusualSpending());
        }

        if (request.getBudgetExceeded() != null) {
            preferences.setBudgetExceeded(request.getBudgetExceeded());
        }

        if (request.getWeeklyExpenseReport() != null) {
            preferences.setWeeklyExpenseReport(request.getWeeklyExpenseReport());
        }

        if (request.getInAppEnabled() != null) {
            preferences.setInAppEnabled(request.getInAppEnabled());
        }

        if (request.getPushEnabled() != null) {
            preferences.setPushEnabled(request.getPushEnabled());
        }

        if (request.getEmailEnabled() != null) {
            preferences.setEmailEnabled(request.getEmailEnabled());
        }

        return preferenceRepository.save(preferences);
    }

    public List<AppNotification> getNotifications(User user) {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public AppNotification markAsRead(User user, Long notificationId) {
        AppNotification notification = notificationRepository
                .findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setReadAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public AppNotification createNotificationIfEnabled(
            User user,
            NotificationType type,
            String title,
            String body,
            String metadata
    ) {
        NotificationPreference preferences = getOrCreatePreferences(user);

        if (!preferences.isInAppEnabled()) {
            return null;
        }

        if (!isTypeEnabled(preferences, type)) {
            return null;
        }

        AppNotification notification = new AppNotification(
                user,
                type,
                title,
                body,
                metadata
        );

        return notificationRepository.save(notification);
    }

    public void notifyTicketAnalysisCompleted(
            User user,
            UUID ticketId,
            String merchantName,
            Double totalAmount
    ) {
        String body = merchantName != null && !merchantName.isBlank()
                ? "Votre ticket " + merchantName + " a été analysé avec succès."
                : "Votre ticket a été analysé avec succès.";

        String metadata = "{"
                + "\"ticketId\":\"" + ticketId + "\","
                + "\"merchantName\":\"" + safeJson(merchantName) + "\","
                + "\"totalAmount\":" + jsonNumber(totalAmount)
                + "}";

        createNotificationIfEnabled(
                user,
                NotificationType.TICKET_ANALYSIS_COMPLETED,
                "Analyse terminée",
                body,
                metadata
        );
    }

    public void notifyBudgetExceeded(
            User user,
            Double currentTotal,
            Double budgetAmount
    ) {
        String body = "Votre budget mensuel de "
                + formatAmount(budgetAmount)
                + " € est dépassé.";

        String metadata = "{"
                + "\"currentTotal\":" + jsonNumber(currentTotal) + ","
                + "\"budgetAmount\":" + jsonNumber(budgetAmount)
                + "}";

        createNotificationIfEnabled(
                user,
                NotificationType.BUDGET_EXCEEDED,
                "Budget dépassé",
                body,
                metadata
        );
    }

    public void notifyUnusualSpending(
            User user,
            UUID ticketId,
            Double amount,
            Double averageAmount
    ) {
        String body = "Une dépense de "
                + formatAmount(amount)
                + " € semble supérieure à votre moyenne habituelle.";

        String metadata = "{"
                + "\"ticketId\":\"" + ticketId + "\","
                + "\"amount\":" + jsonNumber(amount) + ","
                + "\"averageAmount\":" + jsonNumber(averageAmount)
                + "}";

        createNotificationIfEnabled(
                user,
                NotificationType.UNUSUAL_SPENDING,
                "Dépense inhabituelle",
                body,
                metadata
        );
    }

    public void notifyWeeklyExpenseReport(
            User user,
            Double totalAmount,
            Integer ticketCount
    ) {
        int safeTicketCount = ticketCount != null ? ticketCount : 0;

        String body = "Cette semaine : "
                + safeTicketCount
                + " ticket"
                + (safeTicketCount > 1 ? "s" : "")
                + " pour "
                + formatAmount(totalAmount)
                + " €.";

        String metadata = "{"
                + "\"totalAmount\":" + jsonNumber(totalAmount) + ","
                + "\"ticketCount\":" + safeTicketCount
                + "}";

        createNotificationIfEnabled(
                user,
                NotificationType.WEEKLY_EXPENSE_REPORT,
                "Résumé hebdomadaire",
                body,
                metadata
        );
    }

    public void notifyMonthlySpendingSummary(
            User user,
            Double totalAmount,
            Integer ticketCount
    ) {
        int safeTicketCount = ticketCount != null ? ticketCount : 0;

        String body = "Votre total du mois est de "
                + formatAmount(totalAmount)
                + " € pour "
                + safeTicketCount
                + " ticket"
                + (safeTicketCount > 1 ? "s" : "")
                + ".";

        String metadata = "{"
                + "\"totalAmount\":" + jsonNumber(totalAmount) + ","
                + "\"ticketCount\":" + safeTicketCount
                + "}";

        createNotificationIfEnabled(
                user,
                NotificationType.MONTHLY_SPENDING_SUMMARY,
                "Résumé mensuel",
                body,
                metadata
        );
    }

    private boolean isTypeEnabled(NotificationPreference preferences, NotificationType type) {
        return switch (type) {
            case TICKET_ANALYSIS_COMPLETED -> preferences.isTicketAnalysisCompleted();
            case MONTHLY_SPENDING_SUMMARY -> preferences.isMonthlySpendingSummary();
            case UNUSUAL_SPENDING -> preferences.isUnusualSpending();
            case BUDGET_EXCEEDED -> preferences.isBudgetExceeded();
            case WEEKLY_EXPENSE_REPORT -> preferences.isWeeklyExpenseReport();
        };
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String jsonNumber(Double value) {
        if (value == null) {
            return "0";
        }

        return String.format(Locale.US, "%.2f", value);
    }

    private String formatAmount(Double value) {
        if (value == null) {
            return "0.00";
        }

        return String.format(Locale.US, "%.2f", value);
    }
}