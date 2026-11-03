package com.economofar.api.services;

import com.economofar.api.dtos.MerchantCategoryResponse;
import com.economofar.api.dtos.MerchantDetailResponse;
import com.economofar.api.dtos.MerchantItemResponse;
import com.economofar.api.dtos.MerchantListResponse;
import com.economofar.api.dtos.MerchantMonthResponse;
import com.economofar.api.dtos.MerchantPurchaseResponse;
import com.economofar.api.dtos.MerchantSummaryResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ranking of the shops where the money went, and the detail of one shop, over the last months
 * (the current month included, so 12 months is the current month and the 11 before).
 *
 * Only receipts with an amount above zero are used (a receipt dated after today is ignored). For
 * one shop, the month before the period is also read, to compare this month with the last one. A
 * shop or a category is the same whatever the case, the accents and the punctuation. The names
 * that only say that the shop is not known are all gathered under one unknown shop.
 * The spelling shown is the one used on most receipts of the period, then the most recent one,
 * then the first in alphabetical order, so the answer never depends on the order of the receipts.
 * Names that tie are put in alphabetical order without looking at the case or the accents.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class MerchantInsightsCalculator {

    public record Item(String name, Integer quantity, BigDecimal total) {
    }

    public record Purchase(String merchant, String category, BigDecimal amount, LocalDate date, List<Item> items) {
    }

    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;

    static final int MAX_MERCHANTS = 50;
    static final int MAX_RECENT = 5;
    static final int MAX_CATEGORIES = 5;
    static final int MAX_ITEMS = 5;
    static final int MIN_WEEKDAY_TICKETS = 3;
    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );
    static final String UNKNOWN_CATEGORY = "Autre";
    static final String UNKNOWN_MERCHANT = "Commerçant inconnu";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MerchantInsightsCalculator() {
    }

    private record Clean(String merchant, String category, BigDecimal amount, LocalDate date, List<Item> items) {
    }

    private static final class Spelling {
        int count = 0;
        LocalDate latest = null;
    }

    /**
     * Chooses one spelling for all the names that are the same once the case, the accents and the
     * punctuation are ignored.
     */
    private static final class Namer {
        private final Map<String, Map<String, Spelling>> seen = new HashMap<>();
        private final Map<String, String> resolved = new HashMap<>();

        void add(String label, LocalDate date) {
            Spelling spelling = seen
                    .computeIfAbsent(key(label), k -> new HashMap<>())
                    .computeIfAbsent(label, k -> new Spelling());

            spelling.count++;

            if (spelling.latest == null || date.isAfter(spelling.latest)) {
                spelling.latest = date;
            }
        }

        String name(String label) {
            String key = key(label);
            String known = resolved.get(key);

            if (known != null) {
                return known;
            }

            Map<String, Spelling> spellings = seen.get(key);

            if (spellings == null) {
                return label;
            }

            String best = null;
            Spelling bestSpelling = null;

            for (Map.Entry<String, Spelling> entry : spellings.entrySet()) {
                if (best == null || isBetter(entry.getKey(), entry.getValue(), best, bestSpelling)) {
                    best = entry.getKey();
                    bestSpelling = entry.getValue();
                }
            }

            resolved.put(key, best);

            return best;
        }

        private static boolean isBetter(String candidate, Spelling candidateSpelling, String best, Spelling bestSpelling) {
            if (candidateSpelling.count != bestSpelling.count) {
                return candidateSpelling.count > bestSpelling.count;
            }

            int byDate = candidateSpelling.latest.compareTo(bestSpelling.latest);

            if (byDate != 0) {
                return byDate > 0;
            }

            return candidate.compareTo(best) < 0;
        }
    }

    private static final class Group {
        String label = null;
        BigDecimal total = BigDecimal.ZERO;
        int tickets = 0;
        LocalDate last = null;
        final Map<String, BigDecimal> categoryTotals = new HashMap<>();
        final Map<String, String> categoryLabels = new HashMap<>();
    }

    private static final class ItemGroup {
        String label = null;
        long quantity = 0;
        BigDecimal total = BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------
    // Period
    // ------------------------------------------------------------------

    public static int clampMonths(int months) {
        return Math.min(Math.max(months, MIN_MONTHS), MAX_MONTHS);
    }

    /** First day of the oldest month of the period. */
    public static LocalDate periodStart(LocalDate today, int months) {
        return today.withDayOfMonth(1).minusMonths(clampMonths(months) - 1L);
    }

    /** Oldest day that is needed: the period, and the month before for the comparison. */
    public static LocalDate fetchStart(LocalDate today, int months) {
        LocalDate start = periodStart(today, months);
        LocalDate previousMonth = today.withDayOfMonth(1).minusMonths(1);

        return start.isBefore(previousMonth) ? start : previousMonth;
    }

    // ------------------------------------------------------------------
    // Ranking
    // ------------------------------------------------------------------

    public static MerchantListResponse list(List<Purchase> purchases, LocalDate today, int months) {
        requireToday(today);

        int period = clampMonths(months);
        LocalDate from = periodStart(today, period);
        List<Clean> clean = clean(purchases, from, today);

        Namer merchantNames = new Namer();
        Namer categoryNames = new Namer();

        for (Clean item : clean) {
            merchantNames.add(item.merchant(), item.date());
            categoryNames.add(item.category(), item.date());
        }

        Map<String, Group> groups = new HashMap<>();
        BigDecimal grand = BigDecimal.ZERO;

        for (Clean item : clean) {
            grand = grand.add(item.amount());

            Group group = groups.computeIfAbsent(key(item.merchant()), k -> new Group());

            if (group.label == null) {
                group.label = item.merchant();
            }

            group.total = group.total.add(item.amount());
            group.tickets++;

            if (group.last == null || item.date().isAfter(group.last)) {
                group.last = item.date();
            }

            String categoryKey = key(item.category());
            group.categoryTotals.merge(categoryKey, item.amount(), BigDecimal::add);
            group.categoryLabels.putIfAbsent(categoryKey, item.category());
        }

        List<Group> ordered = new ArrayList<>(groups.values());

        ordered.sort(
                Comparator.comparing((Group g) -> g.total).reversed()
                        .thenComparing((Group g) -> g.tickets, Comparator.reverseOrder())
                        .thenComparing((a, b) -> byName(merchantNames.name(a.label), merchantNames.name(b.label)))
        );

        List<MerchantSummaryResponse> rows = new ArrayList<>();

        for (Group group : ordered) {
            if (rows.size() >= MAX_MERCHANTS) {
                break;
            }

            rows.add(new MerchantSummaryResponse(
                    merchantNames.name(group.label),
                    money(group.total),
                    percent(group.total, grand),
                    group.tickets,
                    average(group.total, group.tickets),
                    group.last.toString(),
                    daysBetween(group.last, today),
                    topCategory(group, categoryNames)
            ));
        }

        return new MerchantListResponse(
                period,
                from.toString(),
                today.toString(),
                money(grand),
                clean.size(),
                groups.size(),
                rows
        );
    }

    private static String topCategory(Group group, Namer categoryNames) {
        String bestName = null;
        BigDecimal bestTotal = null;

        for (Map.Entry<String, BigDecimal> entry : group.categoryTotals.entrySet()) {
            String name = categoryNames.name(group.categoryLabels.get(entry.getKey()));

            if (bestName == null
                    || entry.getValue().compareTo(bestTotal) > 0
                    || (entry.getValue().compareTo(bestTotal) == 0 && byName(name, bestName) < 0)) {
                bestName = name;
                bestTotal = entry.getValue();
            }
        }

        return bestName;
    }

    // ------------------------------------------------------------------
    // Detail of one shop
    // ------------------------------------------------------------------

    /**
     * The detail of the shop called {@code name}, or null when there is no receipt of that shop in
     * the period.
     */
    public static MerchantDetailResponse detail(List<Purchase> purchases, String name, LocalDate today, int months) {
        requireToday(today);

        int period = clampMonths(months);
        LocalDate from = periodStart(today, period);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate previousStart = monthStart.minusMonths(1);
        LocalDate previousEnd = previousStart.plusDays(Math.min(today.getDayOfMonth(), previousStart.lengthOfMonth()) - 1L);

        List<Clean> clean = clean(purchases, fetchStart(today, period), today);
        String wanted = merchantKey(name);

        Namer merchantNames = new Namer();
        Namer categoryNames = new Namer();

        BigDecimal grand = BigDecimal.ZERO;
        BigDecimal monthToDate = BigDecimal.ZERO;
        BigDecimal previousSamePeriod = BigDecimal.ZERO;
        List<Clean> mine = new ArrayList<>();

        for (Clean item : clean) {
            boolean inPeriod = !item.date().isBefore(from);

            if (inPeriod) {
                merchantNames.add(item.merchant(), item.date());
                categoryNames.add(item.category(), item.date());
                grand = grand.add(item.amount());
            }

            if (!key(item.merchant()).equals(wanted)) {
                continue;
            }

            if (inPeriod) {
                mine.add(item);
            }

            if (!item.date().isBefore(monthStart)) {
                monthToDate = monthToDate.add(item.amount());
            } else if (!item.date().isBefore(previousStart) && !item.date().isAfter(previousEnd)) {
                previousSamePeriod = previousSamePeriod.add(item.amount());
            }
        }

        if (mine.isEmpty()) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        LocalDate first = null;
        LocalDate last = null;
        Set<LocalDate> days = new TreeSet<>();
        int[] weekdayCounts = new int[8];
        Clean biggest = null;

        Map<YearMonth, BigDecimal> monthTotals = new LinkedHashMap<>();
        Map<YearMonth, Integer> monthTickets = new HashMap<>();

        YearMonth lastMonth = YearMonth.from(today);

        for (YearMonth month = YearMonth.from(from); !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            monthTotals.put(month, BigDecimal.ZERO);
            monthTickets.put(month, 0);
        }

        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        Map<String, String> categoryLabels = new HashMap<>();

        Namer itemNames = new Namer();
        Map<String, ItemGroup> itemGroups = new HashMap<>();

        for (Clean item : mine) {
            total = total.add(item.amount());
            days.add(item.date());

            if (first == null || item.date().isBefore(first)) {
                first = item.date();
            }

            if (last == null || item.date().isAfter(last)) {
                last = item.date();
            }

            if (biggest == null || isBigger(item, biggest)) {
                biggest = item;
            }

            YearMonth month = YearMonth.from(item.date());
            monthTotals.merge(month, item.amount(), BigDecimal::add);
            monthTickets.merge(month, 1, Integer::sum);

            String categoryKey = key(item.category());
            categoryTotals.merge(categoryKey, item.amount(), BigDecimal::add);
            categoryLabels.putIfAbsent(categoryKey, item.category());

            for (Item article : item.items()) {
                String articleName = article == null ? "" : tidy(article.name());
                int quantity = article == null || article.quantity() == null ? 1 : article.quantity();

                if (article == null
                        || key(articleName).isEmpty()
                        || quantity <= 0
                        || article.total() == null
                        || article.total().signum() <= 0) {
                    continue;
                }

                itemNames.add(articleName, item.date());

                ItemGroup group = itemGroups.computeIfAbsent(key(articleName), k -> new ItemGroup());

                if (group.label == null) {
                    group.label = articleName;
                }

                group.quantity += quantity;
                group.total = group.total.add(article.total());
            }
        }

        for (LocalDate day : days) {
            weekdayCounts[day.getDayOfWeek().getValue()]++;
        }

        List<MerchantMonthResponse> series = new ArrayList<>();

        for (Map.Entry<YearMonth, BigDecimal> entry : monthTotals.entrySet()) {
            series.add(new MerchantMonthResponse(
                    entry.getKey().toString(),
                    money(entry.getValue()),
                    monthTickets.get(entry.getKey())
            ));
        }

        List<Map.Entry<String, BigDecimal>> categoryEntries = new ArrayList<>(categoryTotals.entrySet());

        categoryEntries.sort(
                Comparator.comparing((Map.Entry<String, BigDecimal> e) -> e.getValue()).reversed()
                        .thenComparing((a, b) -> byName(
                                categoryNames.name(categoryLabels.get(a.getKey())),
                                categoryNames.name(categoryLabels.get(b.getKey()))
                        ))
        );

        List<MerchantCategoryResponse> categories = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : categoryEntries) {
            if (categories.size() >= MAX_CATEGORIES) {
                break;
            }

            categories.add(new MerchantCategoryResponse(
                    categoryNames.name(categoryLabels.get(entry.getKey())),
                    money(entry.getValue()),
                    percent(entry.getValue(), total)
            ));
        }

        List<ItemGroup> itemOrder = new ArrayList<>(itemGroups.values());

        itemOrder.sort(
                Comparator.comparing((ItemGroup g) -> g.total).reversed()
                        .thenComparing((ItemGroup g) -> g.quantity, Comparator.reverseOrder())
                        .thenComparing((a, b) -> byName(itemNames.name(a.label), itemNames.name(b.label)))
        );

        List<MerchantItemResponse> items = new ArrayList<>();

        for (ItemGroup group : itemOrder) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }

            items.add(new MerchantItemResponse(
                    itemNames.name(group.label),
                    (int) Math.min(group.quantity, Integer.MAX_VALUE),
                    money(group.total)
            ));
        }

        List<Clean> newestFirst = new ArrayList<>(mine);

        newestFirst.sort(
                Comparator.comparing(Clean::date).reversed()
                        .thenComparing(Clean::amount, Comparator.reverseOrder())
                        .thenComparing((a, b) -> byName(a.category(), b.category()))
        );

        List<MerchantPurchaseResponse> recent = new ArrayList<>();

        for (Clean item : newestFirst) {
            if (recent.size() >= MAX_RECENT) {
                break;
            }

            recent.add(purchase(item, categoryNames));
        }

        BigDecimal averageDaysBetween = null;

        if (days.size() >= 2) {
            averageDaysBetween = BigDecimal
                    .valueOf(ChronoUnit.DAYS.between(first, last))
                    .divide(BigDecimal.valueOf(days.size() - 1L), 1, RoundingMode.HALF_UP);
        }

        BigDecimal changePercent = null;

        if (previousSamePeriod.signum() > 0) {
            changePercent = monthToDate
                    .subtract(previousSamePeriod)
                    .multiply(HUNDRED)
                    .divide(previousSamePeriod, 1, RoundingMode.HALF_UP);
        }

        return new MerchantDetailResponse(
                merchantNames.name(mine.get(0).merchant()),
                period,
                from.toString(),
                today.toString(),
                money(total),
                percent(total, grand),
                mine.size(),
                average(total, mine.size()),
                first.toString(),
                last.toString(),
                daysBetween(last, today),
                averageDaysBetween,
                usualWeekday(weekdayCounts, days.size()),
                money(monthToDate),
                money(previousSamePeriod),
                changePercent,
                purchase(biggest, categoryNames),
                recent,
                series,
                categories,
                items
        );
    }

    /**
     * The day of the week (1 = Monday ... 7 = Sunday) on which the shop is visited, when at least
     * MIN_WEEKDAY_TICKETS different days fall on it, no other day of the week has as many, and it
     * covers at least two fifths of all the days with a receipt. Null otherwise.
     */
    private static Integer usualWeekday(int[] counts, int totalDays) {
        int best = 0;
        int bestCount = 0;
        int secondCount = 0;

        for (int day = 1; day <= 7; day++) {
            if (counts[day] > bestCount) {
                secondCount = bestCount;
                bestCount = counts[day];
                best = day;
            } else if (counts[day] > secondCount) {
                secondCount = counts[day];
            }
        }

        if (bestCount >= MIN_WEEKDAY_TICKETS && bestCount > secondCount && bestCount * 5 >= totalDays * 2) {
            return best;
        }

        return null;
    }

    private static boolean isBigger(Clean candidate, Clean best) {
        int byAmount = candidate.amount().compareTo(best.amount());

        if (byAmount != 0) {
            return byAmount > 0;
        }

        int byDate = candidate.date().compareTo(best.date());

        if (byDate != 0) {
            return byDate > 0;
        }

        return byName(candidate.category(), best.category()) < 0;
    }

    private static MerchantPurchaseResponse purchase(Clean item, Namer categoryNames) {
        return new MerchantPurchaseResponse(
                item.date().toString(),
                money(item.amount()),
                categoryNames.name(item.category())
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void requireToday(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }
    }

    private static List<Clean> clean(List<Purchase> purchases, LocalDate lowerBound, LocalDate today) {
        List<Clean> result = new ArrayList<>();

        if (purchases == null) {
            return result;
        }

        for (Purchase purchase : purchases) {
            if (purchase == null
                    || purchase.date() == null
                    || purchase.amount() == null
                    || purchase.amount().signum() <= 0
                    || purchase.date().isBefore(lowerBound)
                    || purchase.date().isAfter(today)) {
                continue;
            }

            result.add(new Clean(
                    merchantLabel(purchase.merchant()),
                    label(purchase.category(), UNKNOWN_CATEGORY),
                    purchase.amount(),
                    purchase.date(),
                    purchase.items() == null ? List.of() : purchase.items()
            ));
        }

        return result;
    }

    private static String tidy(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String label(String value, String fallback) {
        String trimmed = tidy(value);

        if (key(trimmed).isEmpty()) {
            return fallback;
        }

        return trimmed;
    }

    private static String merchantLabel(String value) {
        String shown = label(value, UNKNOWN_MERCHANT);

        return UNKNOWN_MERCHANT_KEYS.contains(key(shown)) ? UNKNOWN_MERCHANT : shown;
    }

    public static String merchantKey(String merchant) {
        return key(merchantLabel(merchant));
    }

    private static int byName(String first, String second) {
        int byKey = key(first).compareTo(key(second));

        return byKey != 0 ? byKey : first.compareTo(second);
    }

    private static String key(String name) {
        return RecurringExpenseDetector.normalize(name);
    }

    private static int daysBetween(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal total, int count) {
        return count > 0
                ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() > 0
                ? part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    }
}
