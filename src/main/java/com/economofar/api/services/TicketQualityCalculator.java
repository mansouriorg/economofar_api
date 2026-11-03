package com.economofar.api.services;

import com.economofar.api.dtos.QualityIssueCountResponse;
import com.economofar.api.dtos.QualityTicketResponse;
import com.economofar.api.dtos.TicketQualityResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the saved tickets that the analyses cannot use properly, so that the user can fix them.
 *
 * A ticket of the last months (1 to 24, counted back from today) can have five kinds of problem:
 * - UNKNOWN_MERCHANT: the shop is empty or unknown ("Commerçant inconnu", "Analyse en cours"...);
 * - NO_CATEGORY: the category is empty or "Autre";
 * - DUPLICATE: another ticket has the same shop, the same amount and the same day (the shop is
 *   the same whatever the case, the accents and the punctuation);
 * - MISSING_AMOUNT: the amount is missing or not above zero;
 * - FUTURE_DATE: the ticket is dated after today (the service counts a ticket dated tomorrow as
 *   today), so that no analysis counts it.
 *
 * A ticket without a date, or dated before the period, is not looked at. The tickets are listed
 * the most recent first (then the biggest amount), at most 100 of them; the counts are exact. The
 * answer never depends on the order of the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class TicketQualityCalculator {

    public record Entry(UUID id, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;

    public static final String UNKNOWN_MERCHANT = "UNKNOWN_MERCHANT";
    public static final String NO_CATEGORY = "NO_CATEGORY";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String MISSING_AMOUNT = "MISSING_AMOUNT";
    public static final String FUTURE_DATE = "FUTURE_DATE";

    static final List<String> TYPES = List.of(UNKNOWN_MERCHANT, NO_CATEGORY, DUPLICATE, MISSING_AMOUNT, FUTURE_DATE);

    static final int MAX_TICKETS = 100;

    static final String UNKNOWN_MERCHANT_NAME = "Commerçant inconnu";
    static final String NO_CATEGORY_NAME = "Sans catégorie";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );

    private static final String OTHER_CATEGORY_KEY = "autre";

    private TicketQualityCalculator() {
    }

    public static int clampMonths(int months) {
        return Math.min(Math.max(months, MIN_MONTHS), MAX_MONTHS);
    }

    /** The first day of the period: {@code months} months back from today. */
    public static LocalDate windowStart(LocalDate today, int months) {
        return today.minusMonths(clampMonths(months));
    }

    public static TicketQualityResponse analyse(List<Entry> entries, LocalDate today, int requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);
        LocalDate from = windowStart(today, months);

        List<Checked> checked = new ArrayList<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.date() == null || entry.date().isBefore(from)) {
                    continue;
                }

                checked.add(new Checked(entry));
            }
        }

        Map<String, Integer> sameTicket = new HashMap<>();

        for (Checked item : checked) {
            if (item.hasAmount()) {
                sameTicket.merge(item.duplicateKey(), 1, Integer::sum);
            }
        }

        int[] counts = new int[TYPES.size()];
        List<Checked> flagged = new ArrayList<>();

        for (Checked item : checked) {
            boolean[] problems = new boolean[TYPES.size()];

            problems[0] = item.merchantKey().isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(item.merchantKey());
            problems[1] = item.categoryKey().isEmpty() || item.categoryKey().equals(OTHER_CATEGORY_KEY);
            problems[2] = item.hasAmount() && sameTicket.get(item.duplicateKey()) > 1;
            problems[3] = !item.hasAmount();
            problems[4] = item.entry.date().isAfter(today);

            boolean any = false;

            for (int i = 0; i < problems.length; i++) {
                if (problems[i]) {
                    counts[i]++;
                    any = true;
                }
            }

            if (any) {
                item.problems = problems;
                flagged.add(item);
            }
        }

        flagged.sort(
                Comparator.comparing((Checked c) -> c.entry.date()).reversed()
                        .thenComparing((Checked c) -> c.entry.amount(), Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
                        .thenComparing((Checked c) -> c.merchantKey())
                        .thenComparing((Checked c) -> c.merchant())
                        .thenComparing((Checked c) -> c.category())
                        .thenComparing((Checked c) -> c.entry.id() == null ? "" : c.entry.id().toString())
        );

        List<QualityTicketResponse> shown = new ArrayList<>();

        for (Checked item : flagged) {
            if (shown.size() >= MAX_TICKETS) {
                break;
            }

            List<String> issues = new ArrayList<>();

            for (int i = 0; i < TYPES.size(); i++) {
                if (item.problems[i]) {
                    issues.add(TYPES.get(i));
                }
            }

            shown.add(new QualityTicketResponse(
                    item.entry.id(),
                    item.entry.date().toString(),
                    item.merchant(),
                    item.category(),
                    item.entry.amount() == null ? null : item.entry.amount().setScale(2, RoundingMode.HALF_UP),
                    issues
            ));
        }

        List<QualityIssueCountResponse> countList = new ArrayList<>();

        for (int i = 0; i < TYPES.size(); i++) {
            countList.add(new QualityIssueCountResponse(TYPES.get(i), counts[i]));
        }

        return new TicketQualityResponse(
                months,
                from.toString(),
                today.toString(),
                checked.size(),
                flagged.size(),
                countList,
                shown
        );
    }

    private static final class Checked {
        final Entry entry;
        private final String merchantText;
        private final String merchantKey;
        private final String categoryText;
        private final String categoryKey;
        boolean[] problems;

        Checked(Entry entry) {
            this.entry = entry;
            this.merchantText = tidy(entry.merchant());
            this.merchantKey = key(merchantText);
            this.categoryText = tidy(entry.category());
            this.categoryKey = key(categoryText);
        }

        boolean hasAmount() {
            return entry.amount() != null && entry.amount().signum() > 0;
        }

        String merchantKey() {
            return merchantKey;
        }

        String categoryKey() {
            return categoryKey;
        }

        String merchant() {
            return merchantKey.isEmpty() ? UNKNOWN_MERCHANT_NAME : merchantText;
        }

        String category() {
            return categoryKey.isEmpty() ? NO_CATEGORY_NAME : categoryText;
        }

        String duplicateKey() {
            return merchantKey + "|" + entry.amount().stripTrailingZeros().toPlainString() + "|" + entry.date();
        }
    }

    private static String tidy(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String key(String text) {
        if (text == null) {
            return "";
        }

        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
