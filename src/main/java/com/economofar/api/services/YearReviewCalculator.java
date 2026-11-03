package com.economofar.api.services;

import com.economofar.api.dtos.YearReviewCategoryResponse;
import com.economofar.api.dtos.YearReviewMerchantResponse;
import com.economofar.api.dtos.YearReviewMonthResponse;
import com.economofar.api.dtos.YearReviewResponse;
import com.economofar.api.dtos.YearReviewTicketResponse;

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
 * Looks back at a year of spending: the 12 months, the categories, the shops where the most was
 * spent, the biggest ticket and the comparison with the previous year.
 *
 * The year is the current one when none is asked for or when a later one is asked for, and the
 * oldest allowed one (10 years back) when an older one is asked for. For the year in progress the
 * part of the previous year up to the same day is compared with it (the 29th of February counts
 * as the 28th of February in a year that does not have one), so that the year is not compared
 * with a whole one; the whole previous year is given as well. A finished year is compared with
 * the whole previous year.
 *
 * Only purchases with an amount above zero, dated in the year or in the year before it and not
 * after today, are used (the service counts a purchase dated tomorrow as today). A category or a
 * shop is the same whatever the case, the accents and the punctuation; an empty category is
 * "Autre"; the shops whose name is empty or unknown are not listed. The name shown is the
 * spelling used by most purchases (the first in alphabetical order when several are used as
 * often). The busiest and the quietest month are only given when the finished months did
 * not all spend the same. The answer never depends on the order of the purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class YearReviewCalculator {

    public record Entry(UUID id, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int MAX_YEARS_BACK = 10;

    static final String OTHER_KEY = "autre";
    static final String OTHER_NAME = "Autre";
    static final String MERGED_NAME = "Autres catégories";
    static final String UNKNOWN_MERCHANT_NAME = "Commerçant inconnu";

    static final int MAX_CATEGORY_ROWS = 7;
    static final int KEPT_CATEGORY_ROWS = 6;
    static final int MAX_MERCHANTS = 5;

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private YearReviewCalculator() {
    }

    public static int clampYear(Integer requested, LocalDate today) {
        int latest = today.getYear();

        if (requested == null || requested > latest) {
            return latest;
        }

        return Math.max(requested, latest - MAX_YEARS_BACK);
    }

    public static YearReviewResponse analyse(List<Entry> entries, LocalDate today, Integer requestedYear) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int year = clampYear(requestedYear, today);
        boolean partial = year == today.getYear();
        int monthsElapsed = partial ? today.getMonthValue() : 12;
        LocalDate previousCutoff = partial ? today.withYear(year - 1) : LocalDate.of(year - 1, 12, 31);

        BigDecimal[] monthTotals = zeros(13);
        int[] monthTickets = new int[13];

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal previousTotal = BigDecimal.ZERO;
        BigDecimal previousFullTotal = BigDecimal.ZERO;
        int tickets = 0;

        Map<String, Group> categoryGroups = new HashMap<>();
        Map<String, Group> merchantGroups = new HashMap<>();
        Entry biggest = null;

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.amount() == null || entry.date() == null) {
                    continue;
                }

                if (entry.amount().signum() <= 0 || entry.date().isAfter(today)) {
                    continue;
                }

                int entryYear = entry.date().getYear();

                if (entryYear == year - 1) {
                    previousFullTotal = previousFullTotal.add(entry.amount());

                    if (!entry.date().isAfter(previousCutoff)) {
                        previousTotal = previousTotal.add(entry.amount());
                    }

                    continue;
                }

                if (entryYear != year) {
                    continue;
                }

                int month = entry.date().getMonthValue();

                monthTotals[month] = monthTotals[month].add(entry.amount());
                monthTickets[month]++;
                total = total.add(entry.amount());
                tickets++;

                String categoryText = tidy(entry.category());
                String categoryKey = key(categoryText);

                if (categoryKey.isEmpty()) {
                    categoryKey = OTHER_KEY;
                    categoryText = OTHER_NAME;
                }

                categoryGroups.computeIfAbsent(categoryKey, k -> new Group()).add(categoryText, entry.amount());

                String merchantText = tidy(entry.merchant());
                String merchantKey = key(merchantText);

                if (!merchantKey.isEmpty() && !UNKNOWN_MERCHANT_KEYS.contains(merchantKey)) {
                    merchantGroups.computeIfAbsent(merchantKey, k -> new Group()).add(merchantText, entry.amount());
                }

                if (biggest == null || isBigger(entry, biggest)) {
                    biggest = entry;
                }
            }
        }

        List<YearReviewMonthResponse> months = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            months.add(new YearReviewMonthResponse(month, money(monthTotals[month]), monthTickets[month]));
        }

        int firstMonth = 0;

        for (int month = 1; month <= 12 && firstMonth == 0; month++) {
            if (monthTickets[month] > 0) {
                firstMonth = month;
            }
        }

        int lastFinished = partial ? today.getMonthValue() - 1 : 12;
        int trackedMonths = firstMonth > 0 && firstMonth <= lastFinished ? lastFinished - firstMonth + 1 : 0;

        BigDecimal monthlyAverage = null;
        Integer busiest = null;
        Integer quietest = null;

        if (trackedMonths > 0) {
            BigDecimal trackedTotal = BigDecimal.ZERO;

            for (int month = firstMonth; month <= lastFinished; month++) {
                trackedTotal = trackedTotal.add(monthTotals[month]);
            }

            monthlyAverage = trackedTotal.divide(BigDecimal.valueOf(trackedMonths), 2, RoundingMode.HALF_UP);
        }

        if (trackedMonths >= 2) {
            int busiestMonth = firstMonth;
            int quietestMonth = firstMonth;

            for (int month = firstMonth + 1; month <= lastFinished; month++) {
                if (monthTotals[month].compareTo(monthTotals[busiestMonth]) > 0) {
                    busiestMonth = month;
                }

                if (monthTotals[month].compareTo(monthTotals[quietestMonth]) < 0) {
                    quietestMonth = month;
                }
            }

            if (monthTotals[busiestMonth].compareTo(monthTotals[quietestMonth]) != 0) {
                busiest = busiestMonth;
                quietest = quietestMonth;
            }
        }

        List<Row> categoryRows = rows(categoryGroups);
        int categoryCount = categoryRows.size();

        if (categoryRows.size() > MAX_CATEGORY_ROWS) {
            BigDecimal mergedTotal = BigDecimal.ZERO;

            for (Row row : categoryRows.subList(KEPT_CATEGORY_ROWS, categoryRows.size())) {
                mergedTotal = mergedTotal.add(row.total());
            }

            categoryRows = new ArrayList<>(categoryRows.subList(0, KEPT_CATEGORY_ROWS));
            categoryRows.add(new Row(MERGED_NAME, mergedTotal, 0));
        }

        List<YearReviewCategoryResponse> categories = new ArrayList<>();

        for (Row row : categoryRows) {
            categories.add(new YearReviewCategoryResponse(row.name(), money(row.total()), share(row.total(), total)));
        }

        List<Row> merchantRows = rows(merchantGroups);
        int merchantCount = merchantRows.size();
        List<YearReviewMerchantResponse> merchants = new ArrayList<>();

        for (Row row : merchantRows.subList(0, Math.min(MAX_MERCHANTS, merchantRows.size()))) {
            merchants.add(new YearReviewMerchantResponse(row.name(), money(row.total()), row.tickets()));
        }

        YearReviewTicketResponse biggestTicket = null;

        if (biggest != null) {
            String merchantText = tidy(biggest.merchant());
            String categoryText = tidy(biggest.category());

            biggestTicket = new YearReviewTicketResponse(
                    biggest.id(),
                    key(merchantText).isEmpty() ? UNKNOWN_MERCHANT_NAME : merchantText,
                    key(categoryText).isEmpty() ? OTHER_NAME : categoryText,
                    biggest.date().toString(),
                    money(biggest.amount())
            );
        }

        BigDecimal delta = total.subtract(previousTotal);

        return new YearReviewResponse(
                year,
                partial,
                monthsElapsed,
                money(total),
                tickets,
                average(total, tickets),
                money(previousTotal),
                money(previousFullTotal),
                money(delta),
                percent(delta, previousTotal),
                trackedMonths,
                monthlyAverage,
                busiest,
                quietest,
                months,
                categoryCount,
                categories,
                merchantCount,
                merchants,
                biggestTicket
        );
    }

    /** The biggest amount, then the latest day, then the smallest id, then the merchant and the category. */
    private static boolean isBigger(Entry candidate, Entry current) {
        int byAmount = candidate.amount().compareTo(current.amount());

        if (byAmount != 0) {
            return byAmount > 0;
        }

        int byDate = candidate.date().compareTo(current.date());

        if (byDate != 0) {
            return byDate > 0;
        }

        int byId = idText(candidate).compareTo(idText(current));

        if (byId != 0) {
            return byId < 0;
        }

        int byMerchant = tidy(candidate.merchant()).compareTo(tidy(current.merchant()));

        if (byMerchant != 0) {
            return byMerchant < 0;
        }

        return tidy(candidate.category()).compareTo(tidy(current.category())) < 0;
    }

    private static String idText(Entry entry) {
        return entry.id() == null ? "" : entry.id().toString();
    }

    private record Row(String name, BigDecimal total, int tickets) {
    }

    private static final class Group {
        final Map<String, Integer> spellings = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int tickets = 0;

        void add(String text, BigDecimal amount) {
            spellings.merge(text, 1, Integer::sum);
            total = total.add(amount);
            tickets++;
        }

        String name() {
            String best = null;
            int bestCount = -1;

            for (Map.Entry<String, Integer> spelling : spellings.entrySet()) {
                int count = spelling.getValue();

                if (count > bestCount || (count == bestCount && spelling.getKey().compareTo(best) < 0)) {
                    best = spelling.getKey();
                    bestCount = count;
                }
            }

            return best;
        }
    }

    /** The groups, the biggest total first, then by name. */
    private static List<Row> rows(Map<String, Group> groups) {
        List<Row> rows = new ArrayList<>();

        for (Group group : groups.values()) {
            rows.add(new Row(group.name(), group.total, group.tickets));
        }

        rows.sort(
                Comparator.comparing((Row r) -> r.total(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Row r) -> r.name())
        );

        return rows;
    }

    private static BigDecimal[] zeros(int size) {
        BigDecimal[] values = new BigDecimal[size];

        for (int i = 0; i < size; i++) {
            values[i] = BigDecimal.ZERO;
        }

        return values;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal total, int tickets) {
        if (tickets <= 0) {
            return null;
        }

        return total.divide(BigDecimal.valueOf(tickets), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal delta, BigDecimal base) {
        if (base.signum() <= 0) {
            return null;
        }

        return delta.multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }

        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static String tidy(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cf}+", "").replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\u03C2', '\u03C3')
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
