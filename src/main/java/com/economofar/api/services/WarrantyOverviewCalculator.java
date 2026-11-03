package com.economofar.api.services;

import com.economofar.api.dtos.WarrantyOverviewResponse;
import com.economofar.api.dtos.WarrantyOverviewItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sorts the tickets that have a warranty date by how much warranty is left on the day today.
 *
 * A warranty that ends today still holds (0 days left). It is "soon" when it ends within
 * {@code soonDays} days (7 to 90, 30 by default), "active" when it ends later, "expired" when it
 * ended in the last 12 months. One that ended before that is only counted. A warranty date more
 * than 50 years away is taken for a typing mistake and ignored, and so is a ticket without id or
 * without warranty date.
 *
 * Soon and active warranties come soonest first, expired ones the most recent first; on a tie the
 * bigger amount comes first, then the smaller id. Each list holds 100 rows at most while the counts
 * stay exact. Amounts count only the values above zero. An empty merchant, and the placeholders
 * of the scan ("Commerçant inconnu", "Magasin inconnu", "Analyse en cours", "Enseigne inconnue",
 * "Inconnu", whatever the case, the accents and the punctuation), are shown as "Enseigne inconnue".
 * The answer never depends on the order of the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class WarrantyOverviewCalculator {

    public record Entry(UUID id, String merchant, String note, BigDecimal amount, LocalDate purchaseDate, LocalDate warrantyDate) {
    }

    public static final int DEFAULT_SOON_DAYS = 30;
    public static final int MIN_SOON_DAYS = 7;
    public static final int MAX_SOON_DAYS = 90;
    public static final int PURCHASE_YEARS = 10;

    static final int EXPIRED_MONTHS = 12;
    static final int FAR_YEARS = 50;
    static final int MAX_ROWS = 100;
    static final int MAX_NOTE = 120;

    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private WarrantyOverviewCalculator() {
    }

    public static int clampSoonDays(Integer requested) {
        if (requested == null) {
            return DEFAULT_SOON_DAYS;
        }

        return Math.max(MIN_SOON_DAYS, Math.min(MAX_SOON_DAYS, requested));
    }

    /** The first day of the purchases that are looked at. */
    public static LocalDate purchaseStart(LocalDate today) {
        return today.minusYears(PURCHASE_YEARS);
    }

    public static WarrantyOverviewResponse analyse(List<Entry> entries, LocalDate today, Integer requestedSoonDays) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int soonDays = clampSoonDays(requestedSoonDays);
        LocalDate oldest = today.minusMonths(EXPIRED_MONTHS);
        LocalDate farthest = today.plusYears(FAR_YEARS);

        List<WarrantyOverviewItemResponse> soon = new ArrayList<>();
        List<WarrantyOverviewItemResponse> active = new ArrayList<>();
        List<WarrantyOverviewItemResponse> expired = new ArrayList<>();
        int older = 0;
        BigDecimal protectedAmount = BigDecimal.ZERO;

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.warrantyDate() == null) {
                    continue;
                }

                LocalDate end = entry.warrantyDate();

                if (end.isAfter(farthest)) {
                    continue;
                }

                if (end.isBefore(oldest)) {
                    older++;
                    continue;
                }

                BigDecimal amount = entry.amount() != null && entry.amount().signum() > 0 ? entry.amount() : BigDecimal.ZERO;
                int daysLeft = (int) ChronoUnit.DAYS.between(today, end);

                WarrantyOverviewItemResponse row = new WarrantyOverviewItemResponse(
                        entry.id(),
                        merchantName(entry.merchant()),
                        noteText(entry.note()),
                        money(amount),
                        entry.purchaseDate() == null ? null : entry.purchaseDate().toString(),
                        end.toString(),
                        daysLeft
                );

                if (daysLeft < 0) {
                    expired.add(row);
                } else {
                    protectedAmount = protectedAmount.add(amount);

                    if (daysLeft <= soonDays) {
                        soon.add(row);
                    } else {
                        active.add(row);
                    }
                }
            }
        }

        Comparator<WarrantyOverviewItemResponse> soonestFirst = Comparator
                .comparingInt(WarrantyOverviewItemResponse::daysLeft)
                .thenComparing(WarrantyOverviewItemResponse::amount, Comparator.reverseOrder())
                .thenComparing(row -> row.id().toString());

        Comparator<WarrantyOverviewItemResponse> latestFirst = Comparator
                .comparingInt(WarrantyOverviewItemResponse::daysLeft).reversed()
                .thenComparing(WarrantyOverviewItemResponse::amount, Comparator.reverseOrder())
                .thenComparing(row -> row.id().toString());

        soon.sort(soonestFirst);
        active.sort(soonestFirst);
        expired.sort(latestFirst);

        return new WarrantyOverviewResponse(
                today.toString(),
                soonDays,
                soon.size(),
                active.size(),
                expired.size(),
                older,
                money(protectedAmount),
                firstRows(soon),
                firstRows(active),
                firstRows(expired)
        );
    }

    private static List<WarrantyOverviewItemResponse> firstRows(List<WarrantyOverviewItemResponse> rows) {
        return new ArrayList<>(rows.subList(0, Math.min(rows.size(), MAX_ROWS)));
    }

    private static String merchantName(String merchant) {
        String text = tidy(merchant);

        return text.isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(key(text)) ? UNKNOWN_MERCHANT : text;
    }

    private static String noteText(String note) {
        String text = tidy(note);

        if (text.isEmpty()) {
            return null;
        }

        if (text.length() <= MAX_NOTE) {
            return text;
        }

        int end = MAX_NOTE;

        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }

        return text.substring(0, end).trim() + "\u2026";
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String tidy(String value) {
        return value == null ? "" : Normalizer.normalize(value.replaceAll("\\p{Cf}+", "").replaceAll("[\\p{Z}\\s]+", " ").trim(), Normalizer.Form.NFC);
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u03A3', '\u03C3')
                .toLowerCase(Locale.ROOT)
                .replace('\u03C2', '\u03C3')
                .replace("\u0153", "oe")
                .replace("\u00E6", "ae")
                .replace("\u00DF", "ss")
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
