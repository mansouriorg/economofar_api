package com.economofar.api.services;

import com.economofar.api.dtos.MonthCompareCategoryResponse;
import com.economofar.api.dtos.MonthComparePointResponse;
import com.economofar.api.dtos.MonthCompareResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compares a month with the month before it: the total, the number and the average of the
 * tickets, the change of every category and the two cumulative curves day by day.
 *
 * The chosen month is the month in progress when it is today's month (a month after it is
 * replaced by it, and a month more than 120 months back by the oldest one allowed). For the month
 * in progress only the days from the 1st to today are compared, with the same days of the
 * previous month (all of it when it is shorter than that), so that the first days of a month are
 * not compared with a whole month; the whole previous month is given as well. A finished month is
 * compared whole with the whole month before it.
 *
 * Only purchases with an amount above zero, dated in one of the two months and not after today,
 * are used (the service counts a purchase dated tomorrow as today). A category is the same
 * whatever the case, the accents and the punctuation; an empty category is "Autre". The name shown
 * is the spelling used by most purchases (the first in alphabetical order when several are used as
 * often). The answer never depends on the order of the purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class MonthCompareCalculator {

    public record Entry(String category, BigDecimal amount, LocalDate date) {
    }

    public static final int MAX_MONTHS_BACK = 120;

    static final String OTHER_KEY = "autre";
    static final String OTHER_NAME = "Autre";
    static final String MERGED_NAME = "Autres catégories";

    static final int MAX_ROWS = 9;
    static final int KEPT_ROWS = 8;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MonthCompareCalculator() {
    }

    public static YearMonth clampMonth(YearMonth requested, LocalDate today) {
        YearMonth latest = YearMonth.from(today);

        if (requested == null || requested.isAfter(latest)) {
            return latest;
        }

        YearMonth earliest = latest.minusMonths(MAX_MONTHS_BACK);

        return requested.isBefore(earliest) ? earliest : requested;
    }

    public static MonthCompareResponse analyse(List<Entry> entries, LocalDate today, YearMonth requestedMonth) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        YearMonth current = clampMonth(requestedMonth, today);
        YearMonth previous = current.minusMonths(1);

        boolean partial = current.equals(YearMonth.from(today));
        int currentLength = current.lengthOfMonth();
        int previousLength = previous.lengthOfMonth();
        int days = partial ? today.getDayOfMonth() : currentLength;
        int previousDays = partial ? Math.min(days, previousLength) : previousLength;

        BigDecimal[] currentDaySums = zeros(currentLength + 1);
        BigDecimal[] previousDaySums = zeros(previousLength + 1);

        BigDecimal currentTotal = BigDecimal.ZERO;
        BigDecimal previousTotal = BigDecimal.ZERO;
        int currentTickets = 0;
        int previousTickets = 0;

        Map<String, Group> groups = new HashMap<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.amount() == null || entry.date() == null) {
                    continue;
                }

                if (entry.amount().signum() <= 0 || entry.date().isAfter(today)) {
                    continue;
                }

                YearMonth month = YearMonth.from(entry.date());
                boolean inCurrent = month.equals(current);
                boolean inPrevious = month.equals(previous);

                if (!inCurrent && !inPrevious) {
                    continue;
                }

                int day = entry.date().getDayOfMonth();

                if (inCurrent) {
                    currentDaySums[day] = currentDaySums[day].add(entry.amount());
                } else {
                    previousDaySums[day] = previousDaySums[day].add(entry.amount());
                }

                if (inPrevious && day > previousDays) {
                    continue;
                }

                String text = tidy(entry.category());
                String key = key(text);

                if (key.isEmpty()) {
                    key = OTHER_KEY;
                    text = OTHER_NAME;
                }

                Group group = groups.computeIfAbsent(key, k -> new Group());
                group.spellings.merge(text, 1, Integer::sum);

                if (inCurrent) {
                    group.current = group.current.add(entry.amount());
                    currentTotal = currentTotal.add(entry.amount());
                    currentTickets++;
                } else {
                    group.previous = group.previous.add(entry.amount());
                    previousTotal = previousTotal.add(entry.amount());
                    previousTickets++;
                }
            }
        }

        BigDecimal previousFullTotal = BigDecimal.ZERO;

        for (BigDecimal sum : previousDaySums) {
            previousFullTotal = previousFullTotal.add(sum);
        }

        List<Row> rows = new ArrayList<>();

        for (Group group : groups.values()) {
            rows.add(new Row(group.name(), group.current, group.previous));
        }

        rows.sort(
                Comparator.comparing((Row r) -> r.delta().abs(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Row r) -> r.current(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Row r) -> r.name())
        );

        int categoryCount = rows.size();

        if (rows.size() > MAX_ROWS) {
            BigDecimal mergedCurrent = BigDecimal.ZERO;
            BigDecimal mergedPrevious = BigDecimal.ZERO;

            for (Row row : rows.subList(KEPT_ROWS, rows.size())) {
                mergedCurrent = mergedCurrent.add(row.current());
                mergedPrevious = mergedPrevious.add(row.previous());
            }

            rows = new ArrayList<>(rows.subList(0, KEPT_ROWS));
            rows.add(new Row(MERGED_NAME, mergedCurrent, mergedPrevious));
        }

        List<MonthCompareCategoryResponse> categories = new ArrayList<>();

        for (Row row : rows) {
            categories.add(new MonthCompareCategoryResponse(
                    row.name(),
                    money(row.current()),
                    money(row.previous()),
                    money(row.delta()),
                    percent(row.delta(), row.previous()),
                    share(row.current(), currentTotal)
            ));
        }

        List<MonthComparePointResponse> points = new ArrayList<>();
        BigDecimal runningCurrent = BigDecimal.ZERO;
        BigDecimal runningPrevious = BigDecimal.ZERO;

        for (int day = 1; day <= Math.max(currentLength, previousLength); day++) {
            BigDecimal currentValue = null;
            BigDecimal previousValue = null;

            if (day <= days) {
                runningCurrent = runningCurrent.add(currentDaySums[day]);
                currentValue = money(runningCurrent);
            }

            if (day <= previousLength) {
                runningPrevious = runningPrevious.add(previousDaySums[day]);
                previousValue = money(runningPrevious);
            }

            points.add(new MonthComparePointResponse(day, currentValue, previousValue));
        }

        BigDecimal delta = currentTotal.subtract(previousTotal);

        return new MonthCompareResponse(
                current.toString(),
                previous.toString(),
                partial,
                days,
                previousDays,
                money(currentTotal),
                money(previousTotal),
                money(previousFullTotal),
                money(delta),
                percent(delta, previousTotal),
                currentTickets,
                previousTickets,
                average(currentTotal, currentTickets),
                average(previousTotal, previousTickets),
                categoryCount,
                categories,
                points
        );
    }

    private record Row(String name, BigDecimal current, BigDecimal previous) {
        BigDecimal delta() {
            return current.subtract(previous);
        }
    }

    private static final class Group {
        final Map<String, Integer> spellings = new HashMap<>();
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;

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

            return best == null ? OTHER_NAME : best;
        }
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
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
