package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Checks and cleans a ticket typed by hand. No Spring dependency: every rule is plain logic.
 * A refused entry throws an IllegalArgumentException whose message is shown to the user.
 */
public final class ManualTicketValidator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    public static final int MAX_MERCHANT_LENGTH = 100;
    public static final int MAX_CATEGORY_LENGTH = 100;
    public static final int MAX_PAYMENT_METHOD_LENGTH = 100;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MIN_YEAR = 2000;
    public static final int FUTURE_TOLERANCE_DAYS = 1;

    private static final int MAX_SCALE = 18;

    private ManualTicketValidator() {
    }

    public record Input(
            String merchantName,
            BigDecimal totalAmount,
            String purchaseDate,
            String category,
            String paymentMethod,
            String note
    ) {
    }

    public record Valid(
            String merchantName,
            BigDecimal totalAmount,
            LocalDate purchaseDate,
            String category,
            String paymentMethod,
            String note
    ) {
    }

    public static Valid validate(Input input, LocalDate today) {
        if (input == null) {
            throw new IllegalArgumentException("Les informations du ticket sont obligatoires.");
        }

        if (today == null) {
            throw new IllegalArgumentException("La date du jour est obligatoire.");
        }

        String merchant = collapse(input.merchantName());

        if (merchant.isEmpty()) {
            throw new IllegalArgumentException("Indiquez le nom du commerçant.");
        }

        if (length(merchant) > MAX_MERCHANT_LENGTH) {
            throw new IllegalArgumentException(
                    "Le nom du commerçant est trop long (" + MAX_MERCHANT_LENGTH + " caractères maximum).");
        }

        BigDecimal amount = cleanAmount(input.totalAmount());
        LocalDate date = cleanDate(input.purchaseDate(), today);

        String category = optionalText(
                input.category(),
                MAX_CATEGORY_LENGTH,
                "Le nom de la catégorie est trop long (" + MAX_CATEGORY_LENGTH + " caractères maximum).",
                true);

        String paymentMethod = optionalText(
                input.paymentMethod(),
                MAX_PAYMENT_METHOD_LENGTH,
                "Le moyen de paiement est trop long (" + MAX_PAYMENT_METHOD_LENGTH + " caractères maximum).",
                true);

        String note = optionalText(
                input.note(),
                MAX_NOTE_LENGTH,
                "La note est trop longue (" + MAX_NOTE_LENGTH + " caractères maximum).",
                false);

        return new Valid(merchant, amount, date, category, paymentMethod, note);
    }

    private static BigDecimal cleanAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Indiquez le montant du ticket.");
        }

        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro.");
        }

        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Le montant est trop élevé (1 000 000 maximum).");
        }

        if (value.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("Le montant ne peut avoir que deux décimales.");
        }

        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);

        if (rounded.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro.");
        }

        if (rounded.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Le montant est trop élevé (1 000 000 maximum).");
        }

        return rounded;
    }

    private static LocalDate cleanDate(String text, LocalDate today) {
        LocalDate date = today;

        if (text != null && !text.isBlank()) {
            date = parseDay(text.trim());
        }

        if (date.isAfter(today.plusDays(FUTURE_TOLERANCE_DAYS))) {
            throw new IllegalArgumentException("La date du ticket ne peut pas être dans le futur.");
        }

        if (date.getYear() < MIN_YEAR) {
            throw new IllegalArgumentException("La date du ticket est trop ancienne.");
        }

        return date;
    }

    private static LocalDate parseDay(String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("La date du ticket est invalide (format attendu : AAAA-MM-JJ).");
    }

    private static String optionalText(String value, int maxLength, String tooLongMessage, boolean singleLine) {
        String cleaned = singleLine ? collapse(value) : (value == null ? "" : value.trim());

        if (collapse(cleaned).isEmpty()) {
            return null;
        }

        if (length(cleaned) > maxLength) {
            throw new IllegalArgumentException(tooLongMessage);
        }

        return cleaned;
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String collapse(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("(?U)\\s+", " ").trim();
    }
}
