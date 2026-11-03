package com.economofar.api.services;

import com.economofar.api.dtos.PaymentUsageCategoryResponse;
import com.economofar.api.dtos.PaymentUsageMethodResponse;
import com.economofar.api.dtos.PaymentUsageMonthResponse;
import com.economofar.api.dtos.PaymentUsageResponse;
import com.economofar.api.dtos.PaymentUsageTicketResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adds up the saved tickets of the period for each payment method: spending, tickets, average
 * basket, share of all the spending, month by month, main categories and latest tickets. The
 * tickets that have no payment method are counted apart.
 *
 * The period goes from the first day of the calendar month that is months - 1 months before the
 * current one (months from 1 to 24, 12 by default, a number out of this range is brought back into
 * it) up to today. A ticket dated outside the period, without date or without id is ignored. Amounts
 * count only the values above zero.
 *
 * A payment method is the same whatever the case, the accents and the punctuation, and it is named
 * as it is written most often (the first in plain character order, capitals before lowercase, when
 * several are written as often).
 * A method without any letter or digit (blank, or only punctuation) is no method: the ticket is
 * counted as unspecified. A category is grouped the same way inside a method, and a ticket without
 * category is in "Autre". An empty merchant and the placeholders of the scan ("Commercant inconnu",
 * "Magasin inconnu", "Analyse en cours", "Enseigne inconnue", "Inconnu") are shown as
 * "Enseigne inconnue".
 *
 * Methods come with most spending first, as it is shown (then more tickets, then the first in
 * character order of the method without case, accents and punctuation), 20 at most with an exact
 * count. The answer never depends on the order of the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class PaymentUsageCalculator {

    public record Entry(UUID id, String merchant, String category, String method, BigDecimal amount, LocalDate date) {
    }

    /** The period that is looked at: its length in months and its first and last days. */
    public record Window(int months, LocalDate from, LocalDate to) {
    }

    public static final int DEFAULT_MONTHS = 12;
    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;

    static final int MAX_METHODS = 20;
    static final int MAX_CATEGORIES = 5;
    static final int MAX_RECENT = 10;

    static final String OTHER_CATEGORY = "Autre";
    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private PaymentUsageCalculator() {
    }

    public static int clampMonths(Integer requested) {
        if (requested == null) {
            return DEFAULT_MONTHS;
        }

        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    /** The first and the last day of the tickets that are looked at. */
    public static Window window(LocalDate today, Integer requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);

        return new Window(months, YearMonth.from(today).minusMonths(months - 1L).atDay(1), today);
    }

    public static PaymentUsageResponse analyse(List<Entry> entries, LocalDate today, Integer requestedMonths) {
        Window window = window(today, requestedMonths);

        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, Method> methods = new HashMap<>();
        List<Row> unspecified = new ArrayList<>();
        BigDecimal unspecifiedAmount = BigDecimal.ZERO;

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(window.from()) || entry.date().isAfter(window.to())) {
                    continue;
                }

                BigDecimal amount = positive(entry.amount());
                String methodText = tidy(entry.method());
                String methodKey = key(methodText);
                Row row = new Row(entry.id(), merchantName(entry.merchant()), categoryLabel(entry.category()), entry.date(), amount);

                count++;
                total = total.add(amount);

                if (methodKey.isEmpty()) {
                    unspecified.add(row);
                    unspecifiedAmount = unspecifiedAmount.add(amount);
                    continue;
                }

                methods.computeIfAbsent(methodKey, Method::new).add(methodText, row);
            }
        }

        List<Method> ordered = new ArrayList<>(methods.values());

        ordered.sort(
                Comparator.comparing((Method m) -> money(m.total), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Method m) -> m.rows.size(), Comparator.<Integer>reverseOrder())
                        .thenComparing((Method m) -> m.key)
        );

        List<PaymentUsageMethodResponse> result = new ArrayList<>();

        for (int i = 0; i < ordered.size() && i < MAX_METHODS; i++) {
            result.add(ordered.get(i).toResponse(window, total));
        }

        return new PaymentUsageResponse(
                window.months(),
                window.from().toString(),
                window.to().toString(),
                count,
                money(total),
                ordered.size(),
                result,
                unspecified.size(),
                money(unspecifiedAmount),
                tickets(unspecified)
        );
    }

    private record Row(UUID id, String merchant, String category, LocalDate date, BigDecimal amount) {
    }

    private static final Comparator<Row> NEWEST_FIRST =
            Comparator.comparing((Row r) -> r.date(), Comparator.<LocalDate>reverseOrder())
                    .thenComparing((Row r) -> r.amount(), Comparator.<BigDecimal>reverseOrder())
                    .thenComparing((Row r) -> r.id().toString());

    private static final class Slice {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        int tickets = 0;
        BigDecimal amount = BigDecimal.ZERO;

        Slice(String key) {
            this.key = key;
        }
    }

    private static final class Method {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        final List<Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        Method(String key) {
            this.key = key;
        }

        void add(String text, Row row) {
            spellings.merge(text, 1, Integer::sum);
            rows.add(row);
            total = total.add(row.amount());
        }

        PaymentUsageMethodResponse toResponse(Window window, BigDecimal allSpending) {
            LocalDate last = rows.get(0).date();
            LocalDate first = last;
            int priced = 0;
            Map<YearMonth, Integer> monthTickets = new HashMap<>();
            Map<YearMonth, BigDecimal> monthAmounts = new HashMap<>();
            Map<String, Slice> categories = new HashMap<>();

            for (Row row : rows) {
                if (row.date().isBefore(first)) {
                    first = row.date();
                }

                if (row.date().isAfter(last)) {
                    last = row.date();
                }

                if (row.amount().signum() > 0) {
                    priced++;
                }

                YearMonth month = YearMonth.from(row.date());

                monthTickets.merge(month, 1, Integer::sum);
                monthAmounts.merge(month, row.amount(), BigDecimal::add);

                Slice slice = categories.computeIfAbsent(key(row.category()), Slice::new);

                slice.spellings.merge(row.category(), 1, Integer::sum);
                slice.tickets++;
                slice.amount = slice.amount.add(row.amount());
            }

            BigDecimal average = priced == 0
                    ? null
                    : total.divide(BigDecimal.valueOf(priced), 2, RoundingMode.HALF_UP);

            BigDecimal share = allSpending.signum() == 0
                    ? null
                    : total.multiply(BigDecimal.valueOf(100)).divide(allSpending, 1, RoundingMode.HALF_UP);

            List<PaymentUsageMonthResponse> months = new ArrayList<>();
            YearMonth lastMonth = YearMonth.from(window.to());

            for (YearMonth month = YearMonth.from(window.from()); !month.isAfter(lastMonth); month = month.plusMonths(1)) {
                months.add(new PaymentUsageMonthResponse(
                        month.toString(),
                        monthTickets.getOrDefault(month, 0),
                        money(monthAmounts.getOrDefault(month, BigDecimal.ZERO))
                ));
            }

            List<Slice> spent = new ArrayList<>();

            for (Slice slice : categories.values()) {
                if (slice.amount.signum() > 0) {
                    spent.add(slice);
                }
            }

            spent.sort(
                    Comparator.comparing((Slice s) -> money(s.amount), Comparator.<BigDecimal>reverseOrder())
                            .thenComparing((Slice s) -> s.tickets, Comparator.<Integer>reverseOrder())
                            .thenComparing((Slice s) -> s.key)
            );

            List<PaymentUsageCategoryResponse> topCategories = new ArrayList<>();

            for (int i = 0; i < spent.size() && i < MAX_CATEGORIES; i++) {
                Slice slice = spent.get(i);

                topCategories.add(new PaymentUsageCategoryResponse(mostUsed(slice.spellings), slice.tickets, money(slice.amount)));
            }

            return new PaymentUsageMethodResponse(
                    key,
                    mostUsed(spellings),
                    rows.size(),
                    money(total),
                    average,
                    share,
                    first.toString(),
                    last.toString(),
                    (int) ChronoUnit.DAYS.between(last, window.to()),
                    months,
                    spent.size(),
                    topCategories,
                    tickets(rows)
            );
        }
    }

    /** The latest tickets, the most recent day first. */
    private static List<PaymentUsageTicketResponse> tickets(List<Row> rows) {
        List<Row> sorted = new ArrayList<>(rows);

        sorted.sort(NEWEST_FIRST);

        List<PaymentUsageTicketResponse> result = new ArrayList<>();

        for (int i = 0; i < sorted.size() && i < MAX_RECENT; i++) {
            Row row = sorted.get(i);

            result.add(new PaymentUsageTicketResponse(row.id(), row.merchant(), row.date().toString(), money(row.amount())));
        }

        return result;
    }

    /** The merchant as written, or "Enseigne inconnue" when there is none or it is a placeholder. */
    private static String merchantName(String merchant) {
        String text = tidy(merchant);
        String key = key(text);

        return key.isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(key) ? UNKNOWN_MERCHANT : text;
    }

    /** The category as written, or "Autre" when there is none. */
    private static String categoryLabel(String category) {
        String text = tidy(category);

        return key(text).isEmpty() ? OTHER_CATEGORY : text;
    }

    private static BigDecimal positive(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    /** The spelling with the most votes, the first in plain character order (capitals first) on a tie. */
    private static String mostUsed(Map<String, Integer> spellings) {
        String best = null;
        int bestVotes = -1;

        for (Map.Entry<String, Integer> spelling : spellings.entrySet()) {
            int votes = spelling.getValue();

            if (votes > bestVotes || (votes == bestVotes && spelling.getKey().compareTo(best) < 0)) {
                best = spelling.getKey();
                bestVotes = votes;
            }
        }

        return best;
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
