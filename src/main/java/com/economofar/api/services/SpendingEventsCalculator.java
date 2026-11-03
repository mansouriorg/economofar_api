package com.economofar.api.services;

import com.economofar.api.dtos.SpendingEventCandidateResponse;
import com.economofar.api.dtos.SpendingEventCandidatesResponse;
import com.economofar.api.dtos.SpendingEventCategoryResponse;
import com.economofar.api.dtos.SpendingEventDayResponse;
import com.economofar.api.dtos.SpendingEventDetailResponse;
import com.economofar.api.dtos.SpendingEventMerchantResponse;
import com.economofar.api.dtos.SpendingEventResponse;
import com.economofar.api.dtos.SpendingEventTicketResponse;
import com.economofar.api.dtos.SpendingEventsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where the spending of an event stands, without any dependency on Spring.
 *
 * The spending of an event is the sum of the amounts of its tickets; a ticket without amount, or with an
 * amount that is not above zero, counts for nothing but is still a ticket of the event. Amounts are
 * counted to the cent.
 *
 * A budget is above zero or missing. The state is "OVER" above the budget, "NEAR" from 80 percent of it
 * to the budget included, "OK" below, "NO_BUDGET" without one. The timing depends on today and on the
 * dates: "UNDATED" without any date; "UPCOMING" before the start; "DONE" after the end; "ONGOING" otherwise
 * (an event that has a start and no end is ongoing for ever).
 *
 * The categories are the categories of the tickets (an empty one is "Autre") and the shops their names
 * (an empty one, or a placeholder such as "Commer\u00e7ant inconnu", is "Enseigne inconnue"), compared without
 * capitals, accents or extra spaces; the name shown is the spelling used most often, the smallest one in a tie.
 */
public final class SpendingEventsCalculator {

    /** An event as it is stored. */
    public record Info(
            long id,
            String name,
            String icon,
            BigDecimal budget,
            LocalDate startDate,
            LocalDate endDate,
            boolean archived,
            LocalDateTime createdAt
    ) {
    }

    /** A saved ticket: its id, its shop and category as written, its amount and the day of the purchase. */
    public record Spend(String ticketId, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    /** The dates a search for tickets covers, both included. */
    public record Range(LocalDate from, LocalDate to) {
    }

    static final String STATE_NO_BUDGET = "NO_BUDGET";
    static final String STATE_OK = "OK";
    static final String STATE_NEAR = "NEAR";
    static final String STATE_OVER = "OVER";

    static final String TIMING_UPCOMING = "UPCOMING";
    static final String TIMING_ONGOING = "ONGOING";
    static final String TIMING_DONE = "DONE";
    static final String TIMING_UNDATED = "UNDATED";

    static final String OTHER_CATEGORY = "Autre";
    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    static final int MAX_CATEGORIES = 30;
    static final int MAX_MERCHANTS = 5;
    static final int MAX_DAYS = 60;
    static final int MAX_TICKETS = 100;
    static final int MAX_CANDIDATES = 100;
    static final int CANDIDATE_DAYS = 180;
    static final int MIN_DAYS_FOR_PROJECTION = 3;

    static final LocalDate SEARCH_FROM = LocalDate.of(2000, 1, 1);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Set<String> UNKNOWN_SHOP_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private SpendingEventsCalculator() {
    }

    // ---- an event

    /** The event with the figures of its spending. */
    public static SpendingEventResponse summarise(Info info, List<Spend> spends, LocalDate today) {
        require(info, today);

        List<Spend> rows = valid(spends);
        BigDecimal budget = budgetOf(info);

        BigDecimal spent = BigDecimal.ZERO;
        Set<LocalDate> days = new HashSet<>();
        LocalDate first = null;
        LocalDate last = null;

        for (Spend row : rows) {
            spent = spent.add(amountOf(row));
            days.add(row.date());

            if (first == null || row.date().isBefore(first)) {
                first = row.date();
            }

            if (last == null || row.date().isAfter(last)) {
                last = row.date();
            }
        }

        spent = money(spent);

        String state = STATE_NO_BUDGET;
        BigDecimal remaining = null;
        BigDecimal usedPercent = null;

        if (budget != null) {
            remaining = budget.subtract(spent);
            usedPercent = spent.multiply(HUNDRED).divide(budget, 1, RoundingMode.HALF_UP);

            if (spent.compareTo(budget) > 0) {
                state = STATE_OVER;
            } else if (spent.multiply(BigDecimal.valueOf(5)).compareTo(budget.multiply(BigDecimal.valueOf(4))) >= 0) {
                state = STATE_NEAR;
            } else {
                state = STATE_OK;
            }
        }

        LocalDate start = info.startDate();
        LocalDate end = info.endDate();
        String timing = timingOf(start, end, today);

        Integer dayCount = start != null && end != null ? days(start, end) : null;

        Integer elapsed = null;

        if (start != null && !start.isAfter(today)) {
            LocalDate until = end != null && end.isBefore(today) ? end : today;
            elapsed = days(start, until);
        }

        Integer daysLeft = null;

        if (end != null && !end.isBefore(today)) {
            LocalDate from = start != null && start.isAfter(today) ? start : today;
            daysLeft = days(from, end);
        }

        BigDecimal dailyAllowance = null;

        if (budget != null && daysLeft != null && remaining.signum() > 0) {
            dailyAllowance = remaining.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP);
        }

        BigDecimal averagePerDay = null;

        if (elapsed != null) {
            averagePerDay = spent.divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        }

        BigDecimal projected = null;

        if (timing.equals(TIMING_ONGOING)
                && dayCount != null
                && elapsed != null
                && elapsed >= MIN_DAYS_FOR_PROJECTION
                && elapsed.longValue() * 4 >= dayCount
                && elapsed < dayCount
                && spent.signum() > 0) {
            projected = spent.multiply(BigDecimal.valueOf(dayCount)).divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        }

        boolean projectedOver = projected != null && budget != null && projected.compareTo(budget) > 0;

        return new SpendingEventResponse(
                info.id(),
                info.name(),
                info.icon(),
                budget,
                start == null ? null : start.toString(),
                end == null ? null : end.toString(),
                info.archived(),
                spent,
                rows.size(),
                days.size(),
                first == null ? null : first.toString(),
                last == null ? null : last.toString(),
                remaining,
                usedPercent,
                state,
                timing,
                dayCount,
                elapsed,
                daysLeft,
                dailyAllowance,
                averagePerDay,
                projected,
                projectedOver
        );
    }

    static String timingOf(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null && end == null) {
            return TIMING_UNDATED;
        }

        if (start != null && today.isBefore(start)) {
            return TIMING_UPCOMING;
        }

        if (end != null && today.isAfter(end)) {
            return TIMING_DONE;
        }

        return TIMING_ONGOING;
    }

    // ---- the events

    /** All the events, in the order the app shows them. */
    public static SpendingEventsResponse overview(List<Info> infos, Map<Long, List<Spend>> spends, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        List<Info> events = new ArrayList<>();

        if (infos != null) {
            for (Info info : infos) {
                if (info != null) {
                    events.add(info);
                }
            }
        }

        Map<Long, SpendingEventResponse> answers = new HashMap<>();

        for (Info info : events) {
            List<Spend> own = spends == null ? null : spends.get(info.id());
            answers.put(info.id(), summarise(info, own, today));
        }

        events.sort(order(answers));

        List<SpendingEventResponse> list = new ArrayList<>();
        int active = 0;
        int archived = 0;
        int ongoing = 0;
        int over = 0;
        int near = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (Info info : events) {
            SpendingEventResponse answer = answers.get(info.id());
            list.add(answer);

            if (info.archived()) {
                archived++;
                continue;
            }

            active++;
            total = total.add(answer.spent());

            if (answer.timing().equals(TIMING_ONGOING)) {
                ongoing++;
            }

            if (answer.state().equals(STATE_OVER)) {
                over++;
            } else if (answer.state().equals(STATE_NEAR)) {
                near++;
            }
        }

        return new SpendingEventsResponse(active, archived, ongoing, over, near, money(total), list);
    }

    private static Comparator<Info> order(Map<Long, SpendingEventResponse> answers) {
        return Comparator
                .comparing((Info info) -> info.archived())
                .thenComparing(info -> timingRank(answers.get(info.id()).timing()))
                .thenComparing(info -> sortDate(info, answers.get(info.id())), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(info -> createdRank(info, answers.get(info.id())), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(info -> SpendingEventRules.nameKey(info.name()))
                .thenComparing(info -> info.id());
    }

    private static int timingRank(String timing) {
        return switch (timing) {
            case TIMING_ONGOING -> 0;
            case TIMING_UPCOMING -> 1;
            case TIMING_UNDATED -> 2;
            default -> 3;
        };
    }

    /** ongoing: the soonest end first; upcoming: the soonest start first; done: the latest end first. */
    private static Long sortDate(Info info, SpendingEventResponse answer) {
        return switch (answer.timing()) {
            case TIMING_ONGOING -> info.endDate() == null ? null : info.endDate().toEpochDay();
            case TIMING_UPCOMING -> info.startDate().toEpochDay();
            case TIMING_DONE -> -info.endDate().toEpochDay();
            default -> null;
        };
    }

    /** undated: the newest first. */
    private static Long createdRank(Info info, SpendingEventResponse answer) {
        if (!answer.timing().equals(TIMING_UNDATED) || info.createdAt() == null) {
            return null;
        }

        return -info.createdAt().toEpochSecond(java.time.ZoneOffset.UTC);
    }

    // ---- the detail of an event

    /** The event with its categories, its shops, its days and its tickets. */
    public static SpendingEventDetailResponse detail(Info info, List<Spend> spends, LocalDate today) {
        SpendingEventResponse event = summarise(info, spends, today);
        List<Spend> rows = valid(spends);

        Map<String, List<Spend>> byCategory = new LinkedHashMap<>();
        Map<String, List<Spend>> byMerchant = new LinkedHashMap<>();
        Map<LocalDate, List<Spend>> byDay = new TreeMap<>();

        for (Spend row : rows) {
            byCategory.computeIfAbsent(categoryKey(row.category()), k -> new ArrayList<>()).add(row);
            byMerchant.computeIfAbsent(merchantKey(row.merchant()), k -> new ArrayList<>()).add(row);
            byDay.computeIfAbsent(row.date(), k -> new ArrayList<>()).add(row);
        }

        BigDecimal total = event.spent();

        List<Group> categories = new ArrayList<>();

        for (Map.Entry<String, List<Spend>> entry : byCategory.entrySet()) {
            categories.add(group(entry.getKey(), entry.getValue(), true));
        }

        categories.sort(groupOrder());

        List<SpendingEventCategoryResponse> categoryAnswers = new ArrayList<>();

        for (int i = 0; i < categories.size() && i < MAX_CATEGORIES; i++) {
            Group group = categories.get(i);
            BigDecimal share = total.signum() > 0
                    ? group.spent.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP)
                    : null;

            categoryAnswers.add(new SpendingEventCategoryResponse(group.name, group.spent, group.rows.size(), share));
        }

        List<Group> merchants = new ArrayList<>();

        for (Map.Entry<String, List<Spend>> entry : byMerchant.entrySet()) {
            merchants.add(group(entry.getKey(), entry.getValue(), false));
        }

        merchants.sort(groupOrder());

        List<SpendingEventMerchantResponse> merchantAnswers = new ArrayList<>();

        for (int i = 0; i < merchants.size() && i < MAX_MERCHANTS; i++) {
            Group group = merchants.get(i);
            merchantAnswers.add(new SpendingEventMerchantResponse(group.name, group.spent, group.rows.size()));
        }

        List<SpendingEventDayResponse> allDays = new ArrayList<>();

        for (Map.Entry<LocalDate, List<Spend>> entry : byDay.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;

            for (Spend row : entry.getValue()) {
                sum = sum.add(amountOf(row));
            }

            allDays.add(new SpendingEventDayResponse(entry.getKey().toString(), money(sum), entry.getValue().size()));
        }

        int hiddenDays = Math.max(allDays.size() - MAX_DAYS, 0);
        List<SpendingEventDayResponse> shownDays = new ArrayList<>(allDays.subList(hiddenDays, allDays.size()));

        List<Spend> newest = new ArrayList<>(rows);
        newest.sort(ticketOrder());

        List<SpendingEventTicketResponse> ticketAnswers = new ArrayList<>();

        for (int i = 0; i < newest.size() && i < MAX_TICKETS; i++) {
            Spend row = newest.get(i);

            ticketAnswers.add(new SpendingEventTicketResponse(
                    row.ticketId(),
                    merchantName(row.merchant()),
                    categoryName(row.category()),
                    amountOf(row),
                    row.date().toString()
            ));
        }

        return new SpendingEventDetailResponse(
                event,
                categories.size(),
                categoryAnswers,
                merchants.size(),
                merchantAnswers,
                hiddenDays,
                shownDays,
                Math.max(newest.size() - MAX_TICKETS, 0),
                ticketAnswers
        );
    }

    private static final class Group {
        final String key;
        final String name;
        final BigDecimal spent;
        final List<Spend> rows;

        Group(String key, String name, BigDecimal spent, List<Spend> rows) {
            this.key = key;
            this.name = name;
            this.spent = spent;
            this.rows = rows;
        }
    }

    private static Group group(String key, List<Spend> rows, boolean category) {
        BigDecimal sum = BigDecimal.ZERO;
        List<String> names = new ArrayList<>();

        for (Spend row : rows) {
            sum = sum.add(amountOf(row));
            names.add(category ? categoryName(row.category()) : merchantName(row.merchant()));
        }

        return new Group(key, mostFrequent(names), money(sum), rows);
    }

    private static Comparator<Group> groupOrder() {
        return Comparator
                .comparing((Group g) -> g.spent).reversed()
                .thenComparing(g -> g.key)
                .thenComparing(g -> g.name);
    }

    private static Comparator<Spend> ticketOrder() {
        return Comparator
                .comparing(Spend::date).reversed()
                .thenComparing(Comparator.comparing(SpendingEventsCalculator::amountOf).reversed())
                .thenComparing(Spend::ticketId);
    }

    // ---- the tickets that can be put in an event

    /** The dates the search covers: the latest 180 days, or everything with a query, and the dates of the event. */
    public static Range candidateRange(Info info, String query, LocalDate today) {
        require(info, today);

        boolean search = !SpendingEventRules.nameKey(query).isEmpty();
        LocalDate from = search ? SEARCH_FROM : today.minusDays(CANDIDATE_DAYS);
        LocalDate to = today.plusDays(1);

        if (info.startDate() != null && info.startDate().isBefore(from)) {
            from = info.startDate();
        }

        if (info.endDate() != null && info.endDate().isAfter(to)) {
            to = info.endDate();
        }

        return new Range(from, to);
    }

    /**
     * The tickets of the pool (which must not hold the tickets that are in an event) that fit the search:
     * the ones within the dates of the event first, then the newest first, 100 at most.
     */
    public static SpendingEventCandidatesResponse candidates(Info info, List<Spend> pool, String query, LocalDate today) {
        Range range = candidateRange(info, query, today);
        String queryKey = SpendingEventRules.nameKey(query);

        List<Spend> found = new ArrayList<>();

        for (Spend row : valid(pool)) {
            if (row.date().isBefore(range.from()) || row.date().isAfter(range.to())) {
                continue;
            }

            if (!queryKey.isEmpty() && !SpendingEventRules.nameKey(merchantName(row.merchant())).contains(queryKey)) {
                continue;
            }

            found.add(row);
        }

        found.sort(Comparator
                .comparing((Spend row) -> !inWindow(info, row.date()))
                .thenComparing(ticketOrder()));

        List<SpendingEventCandidateResponse> answers = new ArrayList<>();

        for (int i = 0; i < found.size() && i < MAX_CANDIDATES; i++) {
            Spend row = found.get(i);

            answers.add(new SpendingEventCandidateResponse(
                    row.ticketId(),
                    merchantName(row.merchant()),
                    categoryName(row.category()),
                    amountOf(row),
                    row.date().toString(),
                    inWindow(info, row.date())
            ));
        }

        return new SpendingEventCandidatesResponse(
                SpendingEventRules.cleanName(query),
                info.startDate() != null,
                range.from().toString(),
                range.to().toString(),
                Math.max(found.size() - MAX_CANDIDATES, 0),
                answers
        );
    }

    private static boolean inWindow(Info info, LocalDate date) {
        return info.startDate() != null
                && !date.isBefore(info.startDate())
                && (info.endDate() == null || !date.isAfter(info.endDate()));
    }

    // ---- names and amounts

    /** The key of a category: its name without capitals, accents or extra spaces; empty is "Autre". */
    static String categoryKey(String category) {
        String key = SpendingEventRules.nameKey(category);

        return key.isEmpty() ? SpendingEventRules.nameKey(OTHER_CATEGORY) : key;
    }

    static String categoryName(String category) {
        String name = SpendingEventRules.cleanName(category);

        return name.isEmpty() ? OTHER_CATEGORY : name;
    }

    /** The key of a shop: its name without capitals, accents or extra spaces; a missing shop has an empty key. */
    static String merchantKey(String merchant) {
        String key = SpendingEventRules.nameKey(merchant);

        return isUnknownShop(key) ? "" : key;
    }

    static String merchantName(String merchant) {
        String name = SpendingEventRules.cleanName(merchant);

        return isUnknownShop(SpendingEventRules.nameKey(name)) ? UNKNOWN_MERCHANT : name;
    }

    /**
     * True for a shop name that is missing, has no letter or digit, or is only a placeholder such as
     * "Enseigne inconnue" or "Commer\u00e7ant inconnu" (capitals, accents and punctuation do not count).
     */
    static boolean isUnknownShop(String shopKey) {
        StringBuilder letters = new StringBuilder();
        shopKey.codePoints().filter(Character::isLetterOrDigit).forEach(letters::appendCodePoint);

        return letters.length() == 0 || UNKNOWN_SHOP_KEYS.contains(letters.toString());
    }

    private static String mostFrequent(List<String> names) {
        Map<String, Integer> counts = new HashMap<>();

        for (String name : names) {
            counts.merge(name, 1, Integer::sum);
        }

        String best = null;
        int bestCount = 0;

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount || (entry.getValue() == bestCount && entry.getKey().compareTo(best) < 0)) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return best;
    }

    /** The tickets that can be counted: the ones that have an id and a date. */
    private static List<Spend> valid(List<Spend> spends) {
        List<Spend> rows = new ArrayList<>();

        if (spends == null) {
            return rows;
        }

        for (Spend spend : spends) {
            if (spend != null && spend.ticketId() != null && spend.date() != null) {
                rows.add(spend);
            }
        }

        return rows;
    }

    /** The amount of a ticket to the cent, zero when it has none or when it is not above zero. */
    static BigDecimal amountOf(Spend spend) {
        if (spend.amount() == null || spend.amount().signum() <= 0) {
            return money(BigDecimal.ZERO);
        }

        return money(spend.amount());
    }

    /** The budget to the cent, null when there is none or when it is not above zero. */
    private static BigDecimal budgetOf(Info info) {
        if (info.budget() == null) {
            return null;
        }

        BigDecimal budget = money(info.budget());

        return budget.signum() > 0 ? budget : null;
    }

    private static int days(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void require(Info info, LocalDate today) {
        if (info == null) {
            throw new IllegalArgumentException("event is required");
        }

        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }
    }
}
