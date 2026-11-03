package com.economofar.api.services;

import com.economofar.api.dtos.SpendingSplitCategoryResponse;
import com.economofar.api.dtos.SpendingSplitGroupResponse;
import com.economofar.api.dtos.SpendingSplitResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits the spending of one calendar month into needs, wants and savings, the 50/30/20 rule: about
 * half of the income for the needs, 30 percent for the wants and 20 percent for the savings.
 *
 * The spending is the one of the saved tickets of the month, counted like the cash flow counts it: the
 * whole calendar month, an amount added as it is (a refund is taken off), a ticket without total adds
 * nothing but is counted as a ticket. The income is the one that is declared for the month, given by the
 * caller, so that the split and the cash flow show the same figure.
 *
 * A ticket goes to the category it names, once the spaces around the name are cut and the capitals are
 * lowered. A category is a need or a want when the app knows it (Alimentation, Transport, Sant\u00e9,
 * Maison... are needs; Shopping, Loisirs, Restaurant... are wants), and it is "unsorted" otherwise, so
 * that nothing is classified by guess. The person can decide otherwise with a rule: a category can be
 * made a need, a want, or left out of the calculation (professional spending that is reimbursed, for
 * example). A ticket without category is unsorted and no rule can apply to it.
 *
 * The savings are what is left of the income once the spending that counts is taken off. This is the
 * net of the cash flow: without any excluded category both give the same figure. A need or a want is
 * "OK" while it is within its share of the income and "OVER" above it; the savings are "OK" when they
 * reach their share and "UNDER" below it. A share is rounded to the cent first, and the percent that is
 * shown never crosses the line of the status (so a need that is over never reads under 50 percent).
 * Without income no slice can be compared and every status is "NONE".
 *
 * The month is "yyyy-MM", the current month when it is not given; a month after the current one or more
 * than 23 months before it is brought back into this range. The answer never depends on the order of
 * the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class SpendingSplitCalculator {

    public record Entry(String category, BigDecimal amount, LocalDate date) {
    }

    /** The month that is looked at: its first and last days, and the months next to it. */
    public record Period(YearMonth month, boolean current, LocalDate from, LocalDate to, String previous, String next) {
    }

    public static final int MAX_MONTHS_BACK = 23;
    public static final int MAX_KEY_LENGTH = 120;
    public static final int MAX_CATEGORIES = 60;

    public static final String NEED = "NEED";
    public static final String WANT = "WANT";
    public static final String SAVINGS = "SAVINGS";
    public static final String UNSORTED = "UNSORTED";
    public static final String EXCLUDED = "EXCLUDED";

    static final int NEED_PERCENT = 50;
    static final int WANT_PERCENT = 30;
    static final int SAVINGS_PERCENT = 20;

    static final String STATUS_OK = "OK";
    static final String STATUS_OVER = "OVER";
    static final String STATUS_UNDER = "UNDER";
    static final String STATUS_NONE = "NONE";

    static final String NO_CATEGORY = "Sans cat\u00e9gorie";

    private static final BigDecimal ONE_TENTH = new BigDecimal("0.1");

    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");

    /** Categories that are needs. The names are written without accents, capitals or punctuation. */
    private static final Set<String> NEED_NAMES = Set.of(
            "alimentation", "courses", "supermarche", "epicerie",
            "transport", "transports", "carburant", "essence",
            "sante", "pharmacie", "medecin",
            "maison", "logement", "loyer", "energie", "electricite", "eau",
            "assurance", "assurances", "telephonie", "internet",
            "education", "scolarite"
    );

    /** Categories that are wants. */
    private static final Set<String> WANT_NAMES = Set.of(
            "shopping", "loisirs", "restaurant", "restaurants", "sorties", "cafe",
            "voyage", "voyages", "vacances", "cadeaux", "culture", "divertissement", "jeux"
    );

    /** When two rules say something different for the same category, the first of these wins. */
    private static final List<String> RULE_ORDER = List.of(EXCLUDED, NEED, WANT);

    private static final List<String> LINE_ORDER = List.of(NEED, WANT, UNSORTED, EXCLUDED);

    private SpendingSplitCalculator() {
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

    /**
     * The key of a category: its name without invisible characters, with single spaces, without the
     * spaces around it, without capitals. Empty when there is no name. A rule is kept under this key and a
     * ticket is matched with a rule through it.
     */
    public static String categoryKey(String category) {
        if (category == null) {
            return "";
        }

        String clean = category.replaceAll("\\p{Cf}+", "").replaceAll("[\\p{Z}\\s]+", " ").trim();

        return Normalizer.normalize(clean.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /** Where the app puts a category when the person has not decided: "NEED", "WANT" or "UNSORTED". */
    public static String defaultGroup(String category) {
        String name = Normalizer.normalize(categoryKey(category), Normalizer.Form.NFD)
                .replaceAll("[^\\p{L}\\p{N}]+", "");

        if (NEED_NAMES.contains(name)) {
            return NEED;
        }

        if (WANT_NAMES.contains(name)) {
            return WANT;
        }

        return UNSORTED;
    }

    /** The group of a rule as the person writes it, or null when it is none of "NEED", "WANT" and "EXCLUDED". */
    public static String ruleGroup(String group) {
        if (group == null) {
            return null;
        }

        String text = group.trim().toUpperCase(Locale.ROOT);

        return RULE_ORDER.contains(text) ? text : null;
    }

    /**
     * rules maps the key of a category to "NEED", "WANT" or "EXCLUDED"; an entry that can not be read is
     * ignored. income is the income of the month, null or zero when there is none.
     */
    public static SpendingSplitResponse analyse(List<Entry> entries, Map<String, String> rules, BigDecimal income, LocalDate today, String requestedMonth) {
        Period period = period(today, requestedMonth);

        Map<String, String> chosen = readRules(rules);

        int ticketCount = 0;
        Map<String, Bucket> buckets = new HashMap<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(period.from()) || entry.date().isAfter(period.to())) {
                    continue;
                }

                ticketCount++;

                String key = categoryKey(entry.category());

                buckets.computeIfAbsent(key, Bucket::new).add(entry.category(), entry.amount());
            }
        }

        BigDecimal needRaw = BigDecimal.ZERO;
        BigDecimal wantRaw = BigDecimal.ZERO;
        BigDecimal unsortedRaw = BigDecimal.ZERO;
        BigDecimal excludedRaw = BigDecimal.ZERO;
        int needCount = 0;
        int wantCount = 0;
        int unsortedCount = 0;
        int excludedCount = 0;

        List<Line> lines = new ArrayList<>();

        for (Bucket bucket : buckets.values()) {
            String proposed = bucket.key.isEmpty() ? UNSORTED : defaultGroup(bucket.key);
            String rule = bucket.key.isEmpty() ? null : chosen.get(bucket.key);
            String group = rule == null ? proposed : rule;

            switch (group) {
                case NEED -> {
                    needRaw = needRaw.add(bucket.amount);
                    needCount++;
                }
                case WANT -> {
                    wantRaw = wantRaw.add(bucket.amount);
                    wantCount++;
                }
                case EXCLUDED -> {
                    excludedRaw = excludedRaw.add(bucket.amount);
                    excludedCount++;
                }
                default -> {
                    unsortedRaw = unsortedRaw.add(bucket.amount);
                    unsortedCount++;
                }
            }

            lines.add(new Line(bucket, group, proposed, rule != null));
        }

        BigDecimal countedRaw = needRaw.add(wantRaw).add(unsortedRaw);
        BigDecimal spent = money(countedRaw.signum() < 0 ? BigDecimal.ZERO : countedRaw);

        BigDecimal incomeAmount = income == null || income.signum() < 0 ? money(BigDecimal.ZERO) : money(income);
        boolean hasIncome = incomeAmount.signum() > 0;
        BigDecimal shareBase = countedRaw.signum() > 0 ? countedRaw : null;

        List<SpendingSplitGroupResponse> groups = new ArrayList<>();

        groups.add(spendingSlice(NEED, NEED_PERCENT, needRaw, needCount, incomeAmount, hasIncome, shareBase));
        groups.add(spendingSlice(WANT, WANT_PERCENT, wantRaw, wantCount, incomeAmount, hasIncome, shareBase));
        groups.add(savingsSlice(incomeAmount, hasIncome, spent));
        groups.add(unsortedSlice(unsortedRaw, unsortedCount, incomeAmount, hasIncome, shareBase));

        lines.sort(LINE_SORT);

        List<SpendingSplitCategoryResponse> categories = new ArrayList<>();

        for (int i = 0; i < lines.size() && i < MAX_CATEGORIES; i++) {
            Line line = lines.get(i);

            categories.add(new SpendingSplitCategoryResponse(
                    line.bucket.key,
                    line.bucket.label(),
                    line.group,
                    line.proposed,
                    line.custom,
                    !line.bucket.key.isEmpty() && line.bucket.key.length() <= MAX_KEY_LENGTH,
                    line.bucket.count,
                    money(line.bucket.amount)
            ));
        }

        return new SpendingSplitResponse(
                period.month().toString(),
                period.current(),
                period.previous(),
                period.next(),
                ticketCount,
                incomeAmount,
                hasIncome,
                spent,
                money(excludedRaw),
                excludedCount,
                groups,
                lines.size(),
                categories
        );
    }

    // MARK: Slices

    /** A need or a want: a part of the income that should not be passed. */
    private static SpendingSplitGroupResponse spendingSlice(String code, int target, BigDecimal raw, int categories, BigDecimal income, boolean hasIncome, BigDecimal shareBase) {
        BigDecimal amount = money(raw);
        BigDecimal targetPercent = BigDecimal.valueOf(target).setScale(1, RoundingMode.UNNECESSARY);
        BigDecimal share = shareBase == null ? null : percent(raw, shareBase);

        if (!hasIncome) {
            return new SpendingSplitGroupResponse(code, targetPercent, amount, null, null, null, STATUS_NONE, share, categories);
        }

        BigDecimal targetAmount = targetOf(income, target);
        boolean over = amount.compareTo(targetAmount) > 0;
        BigDecimal shown = percent(amount, income);

        shown = over ? shown.max(targetPercent.add(ONE_TENTH)) : shown.min(targetPercent);

        return new SpendingSplitGroupResponse(
                code,
                targetPercent,
                amount,
                shown,
                targetAmount,
                amount.subtract(targetAmount),
                over ? STATUS_OVER : STATUS_OK,
                share,
                categories
        );
    }

    /** What is left of the income: a part that should be reached. */
    private static SpendingSplitGroupResponse savingsSlice(BigDecimal income, boolean hasIncome, BigDecimal spent) {
        BigDecimal targetPercent = BigDecimal.valueOf(SAVINGS_PERCENT).setScale(1, RoundingMode.UNNECESSARY);

        if (!hasIncome) {
            return new SpendingSplitGroupResponse(SAVINGS, targetPercent, null, null, null, null, STATUS_NONE, null, 0);
        }

        BigDecimal saved = income.subtract(spent);
        BigDecimal targetAmount = targetOf(income, SAVINGS_PERCENT);
        boolean under = saved.compareTo(targetAmount) < 0;
        BigDecimal shown = percent(saved, income);

        shown = under ? shown.min(targetPercent.subtract(ONE_TENTH)) : shown.max(targetPercent);

        return new SpendingSplitGroupResponse(
                SAVINGS,
                targetPercent,
                saved,
                shown,
                targetAmount,
                saved.subtract(targetAmount),
                under ? STATUS_UNDER : STATUS_OK,
                null,
                0
        );
    }

    /** What is not classified: it has no target. */
    private static SpendingSplitGroupResponse unsortedSlice(BigDecimal raw, int categories, BigDecimal income, boolean hasIncome, BigDecimal shareBase) {
        BigDecimal amount = money(raw);

        return new SpendingSplitGroupResponse(
                UNSORTED,
                null,
                amount,
                hasIncome ? percent(amount, income) : null,
                null,
                null,
                STATUS_NONE,
                shareBase == null ? null : percent(raw, shareBase),
                categories
        );
    }

    // MARK: Rules

    private static Map<String, String> readRules(Map<String, String> rules) {
        Map<String, String> chosen = new HashMap<>();

        if (rules == null) {
            return chosen;
        }

        for (Map.Entry<String, String> rule : rules.entrySet()) {
            String key = categoryKey(rule.getKey());
            String group = ruleGroup(rule.getValue());

            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH || group == null) {
                continue;
            }

            chosen.merge(key, group, (first, second) -> RULE_ORDER.indexOf(first) <= RULE_ORDER.indexOf(second) ? first : second);
        }

        return chosen;
    }

    // MARK: Categories

    private static final class Bucket {
        final String key;
        BigDecimal amount = BigDecimal.ZERO;
        int count;
        final Map<String, Integer> spellings = new HashMap<>();

        Bucket(String key) {
            this.key = key;
        }

        void add(String category, BigDecimal value) {
            count++;

            if (value != null) {
                amount = amount.add(value);
            }

            if (!key.isEmpty()) {
                spellings.merge(tidy(category), 1, Integer::sum);
            }
        }

        /** The spelling with the most tickets, the first in plain character order (capitals first) on a tie. */
        String label() {
            if (key.isEmpty()) {
                return NO_CATEGORY;
            }

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
    }

    private record Line(Bucket bucket, String group, String proposed, boolean custom) {
    }

    private static final Comparator<Line> LINE_SORT = Comparator
            .comparingInt((Line line) -> LINE_ORDER.indexOf(line.group))
            .thenComparing((Line line) -> money(line.bucket.amount), Comparator.reverseOrder())
            .thenComparing(line -> line.bucket.key);

    // MARK: Numbers and text

    /** The share of the income for a slice, rounded to the cent. */
    private static BigDecimal targetOf(BigDecimal income, int percent) {
        return income.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** part divided by whole in percent with one decimal; the whole must be above zero. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** The text without invisible characters, with single spaces and nothing around, in its usual form. */
    private static String tidy(String value) {
        return value == null ? "" : Normalizer.normalize(value.replaceAll("\\p{Cf}+", "").replaceAll("[\\p{Z}\\s]+", " ").trim(), Normalizer.Form.NFC);
    }
}
