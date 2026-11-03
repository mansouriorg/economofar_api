package com.economofar.api.services;

import com.economofar.api.dtos.BudgetTrackingLineResponse;
import com.economofar.api.dtos.BudgetTrackingOtherResponse;
import com.economofar.api.dtos.BudgetTrackingResponse;
import com.economofar.api.dtos.BudgetTrackingTicketResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Follows the monthly budgets of the categories against the saved tickets of one calendar month.
 *
 * The budgets are the ones the person keeps on the "Budgets par cat\u00e9gorie" screen. The spending is
 * counted the way that screen counts it, so that both show the same figure: a ticket belongs to the
 * budget whose category is the same as its own once the spaces around them are cut and the capitals are
 * lowered (no other change: accents and punctuation count), the whole calendar month is counted, also
 * the days after today, and an amount is added as it is. A ticket without total adds nothing but it is
 * still listed. A ticket without category, or with a blank one, belongs to no budget: it is listed
 * apart as "Sans cat\u00e9gorie". A ticket without id or without date is ignored, and so is one dated
 * outside the month.
 *
 * The month is "yyyy-MM", the current month when it is not given; a month after the current one or
 * more than 23 months before it is brought back into this range. The categories that have spending and
 * no budget are listed apart, the biggest first, 10 at most with an exact count.
 *
 * A budget is exceeded ("OVER") when what was spent is more than the budget and close to the limit
 * ("WARNING") from 80 percent of it. The percent that is shown never crosses the line of the status
 * (above 100.0 for an exceeded budget, below 80.0 for a calm one), so the budgets come with the
 * highest percent first: the exceeded ones, then the ones close to the limit, then the others. A tie
 * goes to the bigger spending, then to the key. A budget that is not above zero is ignored, and when
 * two budgets have the same category the first one is kept. The answer never depends on the order of
 * the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class BudgetTrackingCalculator {

    public record Entry(UUID id, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public record Budget(String name, BigDecimal amount) {
    }

    /** The month that is looked at: its first and last days, and the months next to it. */
    public record Period(YearMonth month, boolean current, LocalDate from, LocalDate to, String previous, String next) {
    }

    public static final int MAX_MONTHS_BACK = 23;

    static final int WARNING_PERCENT = 80;
    static final int PROJECTION_FROM_DAY = 5;
    static final int MAX_OTHERS = 10;
    static final int MAX_RECENT = 10;

    static final String STATUS_OK = "OK";
    static final String STATUS_WARNING = "WARNING";
    static final String STATUS_OVER = "OVER";

    private static final BigDecimal JUST_OVER_PERCENT = new BigDecimal("100.1");
    private static final BigDecimal JUST_UNDER_WARNING_PERCENT = BigDecimal.valueOf(WARNING_PERCENT).subtract(new BigDecimal("0.1"));

    static final String NO_CATEGORY = "Sans cat\u00e9gorie";
    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private BudgetTrackingCalculator() {
    }

    /** The month that is looked at. A month that can not be read is refused with an IllegalArgumentException. */
    public static Period period(LocalDate today, String requestedMonth) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        YearMonth current = YearMonth.from(today);
        YearMonth earliest = current.minusMonths(MAX_MONTHS_BACK);
        YearMonth chosen = current;
        String text = requestedMonth == null ? "" : requestedMonth.trim();

        if (!text.isEmpty()) {
            if (!MONTH.matcher(text).matches()) {
                throw new IllegalArgumentException("Invalid month: expected yyyy-MM");
            }

            try {
                chosen = YearMonth.parse(text);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid month: expected yyyy-MM");
            }

            if (chosen.isAfter(current)) {
                chosen = current;
            }

            if (chosen.isBefore(earliest)) {
                chosen = earliest;
            }
        }

        return new Period(
                chosen,
                chosen.equals(current),
                chosen.atDay(1),
                chosen.atEndOfMonth(),
                chosen.isAfter(earliest) ? chosen.minusMonths(1).toString() : null,
                chosen.isBefore(current) ? chosen.plusMonths(1).toString() : null
        );
    }

    public static BudgetTrackingResponse analyse(List<Entry> entries, List<Budget> budgets, LocalDate today, String requestedMonth) {
        Period period = period(today, requestedMonth);

        int daysInMonth = period.month().lengthOfMonth();
        int dayOfMonth = period.current() ? today.getDayOfMonth() : daysInMonth;
        int daysLeft = period.current() ? daysInMonth - dayOfMonth + 1 : 0;

        int count = 0;
        BigDecimal monthTotal = BigDecimal.ZERO;
        Map<String, Group> groups = new HashMap<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(period.from()) || entry.date().isAfter(period.to())) {
                    continue;
                }

                BigDecimal amount = entry.amount();
                Row row = new Row(entry.id(), merchantName(entry.merchant()), entry.date(), amount);

                count++;
                monthTotal = monthTotal.add(orZero(amount));

                groups.computeIfAbsent(matchKey(entry.category()), Group::new).add(categoryLabel(entry.category()), row);
            }
        }

        Map<String, Budget> wanted = new LinkedHashMap<>();

        if (budgets != null) {
            for (Budget budget : budgets) {
                if (budget == null || budget.amount() == null) {
                    continue;
                }

                BigDecimal amount = money(budget.amount());

                if (amount.signum() <= 0) {
                    continue;
                }

                String key = matchKey(budget.name());

                if (!key.isEmpty()) {
                    wanted.putIfAbsent(key, new Budget(budgetLabel(budget.name()), amount));
                }
            }
        }

        List<BudgetTrackingLineResponse> lines = new ArrayList<>();
        BigDecimal budgetTotal = BigDecimal.ZERO;
        BigDecimal budgetSpent = BigDecimal.ZERO;
        int warningCount = 0;
        int overCount = 0;

        for (Map.Entry<String, Budget> item : wanted.entrySet()) {
            Group group = groups.get(item.getKey());
            BigDecimal budget = item.getValue().amount();
            BigDecimal spent = group == null ? BigDecimal.ZERO.setScale(2) : money(group.amount);
            BigDecimal remaining = budget.subtract(spent);

            String status = status(spent, budget);

            if (status.equals(STATUS_OVER)) {
                overCount++;
            } else if (status.equals(STATUS_WARNING)) {
                warningCount++;
            }

            BigDecimal projected = null;

            if (period.current() && dayOfMonth >= PROJECTION_FROM_DAY && spent.signum() > 0) {
                projected = spent.multiply(BigDecimal.valueOf(daysInMonth))
                        .divide(BigDecimal.valueOf(dayOfMonth), 2, RoundingMode.HALF_UP);
            }

            BigDecimal dailyAllowance = null;

            if (daysLeft > 0) {
                BigDecimal share = remaining.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP);

                if (share.signum() > 0) {
                    dailyAllowance = share;
                }
            }

            budgetTotal = budgetTotal.add(budget);
            budgetSpent = budgetSpent.add(spent);

            lines.add(new BudgetTrackingLineResponse(
                    item.getKey(),
                    item.getValue().name(),
                    budget,
                    spent,
                    remaining,
                    percentFor(spent, budget, status),
                    status,
                    group == null ? 0 : group.rows.size(),
                    projected,
                    projected != null && projected.compareTo(budget) > 0,
                    dailyAllowance,
                    group == null ? List.of() : tickets(group.rows)
            ));
        }

        lines.sort(
                Comparator.comparing((BudgetTrackingLineResponse l) -> l.percentUsed(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((BudgetTrackingLineResponse l) -> l.spent(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((BudgetTrackingLineResponse l) -> l.key())
        );

        List<Group> unbudgeted = new ArrayList<>();
        BigDecimal otherSpent = BigDecimal.ZERO;

        for (Group group : groups.values()) {
            if (!wanted.containsKey(group.key) && money(group.amount).signum() > 0) {
                unbudgeted.add(group);
                otherSpent = otherSpent.add(group.amount);
            }
        }

        unbudgeted.sort(
                Comparator.comparing((Group g) -> money(g.amount), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Group g) -> g.rows.size(), Comparator.<Integer>reverseOrder())
                        .thenComparing((Group g) -> g.key)
        );

        List<BudgetTrackingOtherResponse> others = new ArrayList<>();

        for (int i = 0; i < unbudgeted.size() && i < MAX_OTHERS; i++) {
            Group group = unbudgeted.get(i);

            others.add(new BudgetTrackingOtherResponse(group.key, mostUsed(group.spellings), group.rows.size(), money(group.amount)));
        }

        return new BudgetTrackingResponse(
                period.month().toString(),
                period.current(),
                period.previous(),
                period.next(),
                dayOfMonth,
                daysInMonth,
                daysLeft,
                count,
                money(monthTotal),
                money(budgetTotal),
                money(budgetSpent),
                money(budgetTotal.subtract(budgetSpent)),
                budgetTotal.signum() > 0 ? percent(budgetSpent, budgetTotal) : null,
                warningCount,
                overCount,
                lines,
                unbudgeted.size(),
                money(otherSpent),
                others
        );
    }

    private record Row(UUID id, String merchant, LocalDate date, BigDecimal amount) {
    }

    private static final Comparator<Row> NEWEST_FIRST =
            Comparator.comparing((Row r) -> r.date(), Comparator.<LocalDate>reverseOrder())
                    .thenComparing((Row r) -> orZero(r.amount()), Comparator.<BigDecimal>reverseOrder())
                    .thenComparing((Row r) -> r.id().toString());

    private static final class Group {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        final List<Row> rows = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;

        Group(String key) {
            this.key = key;
        }

        void add(String text, Row row) {
            spellings.merge(text, 1, Integer::sum);
            rows.add(row);
            amount = amount.add(orZero(row.amount()));
        }
    }

    private static String status(BigDecimal spent, BigDecimal budget) {
        if (spent.compareTo(budget) > 0) {
            return STATUS_OVER;
        }

        if (spent.multiply(BigDecimal.valueOf(100)).compareTo(budget.multiply(BigDecimal.valueOf(WARNING_PERCENT))) >= 0) {
            return STATUS_WARNING;
        }

        return STATUS_OK;
    }

    /** part divided by whole in percent with one decimal; the whole must be above zero. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /**
     * The percent of a budget that is shown with its status. Rounding to one decimal never moves it
     * across the line of the status: an exceeded budget is above 100.0 and a calm one is below 80.0.
     */
    private static BigDecimal percentFor(BigDecimal spent, BigDecimal budget, String status) {
        BigDecimal value = percent(spent, budget);

        if (status.equals(STATUS_OVER)) {
            return value.max(JUST_OVER_PERCENT);
        }

        if (status.equals(STATUS_OK)) {
            return value.min(JUST_UNDER_WARNING_PERCENT);
        }

        return value;
    }

    /** The latest tickets, the most recent day first. */
    private static List<BudgetTrackingTicketResponse> tickets(List<Row> rows) {
        List<Row> sorted = new ArrayList<>(rows);

        sorted.sort(NEWEST_FIRST);

        List<BudgetTrackingTicketResponse> result = new ArrayList<>();

        for (int i = 0; i < sorted.size() && i < MAX_RECENT; i++) {
            Row row = sorted.get(i);

            result.add(new BudgetTrackingTicketResponse(
                    row.id(),
                    row.merchant(),
                    row.date().toString(),
                    row.amount() == null ? null : money(row.amount())
            ));
        }

        return result;
    }

    /** The merchant as written, or "Enseigne inconnue" when there is none or it is a placeholder. */
    private static String merchantName(String merchant) {
        String text = tidy(merchant);
        String key = foldKey(text);

        return key.isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(key) ? UNKNOWN_MERCHANT : text;
    }

    /** The category as written, or "Sans cat\u00e9gorie" when there is none. */
    private static String categoryLabel(String category) {
        String text = tidy(category);

        return text.isEmpty() ? NO_CATEGORY : text;
    }

    /** The name of a budget as written; its own text without the spaces around it when it is only invisible characters. */
    private static String budgetLabel(String name) {
        String text = tidy(name);

        return text.isEmpty() ? name.trim() : text;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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

    /** The text of a category as the budgets screen reads it: without the spaces around it and without capitals. */
    private static String matchKey(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /** The text without case, accents (they are marks once decomposed) and punctuation. */
    private static String foldKey(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
