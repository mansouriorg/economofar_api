package com.economofar.api.services;

import com.economofar.api.dtos.BusinessExpensesResponse;
import com.economofar.api.dtos.BusinessGroupResponse;
import com.economofar.api.dtos.BusinessSectionResponse;
import com.economofar.api.dtos.BusinessTicketResponse;

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
import java.util.function.Function;

/**
 * Adds up the business tickets (the ones marked as professional) of one period: the business total
 * and its share of the spending, the VAT, the deductible and the reimbursable amounts, then the
 * tickets grouped by expense report, by project and by client, with the tickets that have no such
 * label apart.
 *
 * The period is month (from the first day of the month to today), quarter (from the first day of
 * the quarter to today), year (from January 1 to today) or previous-year (the whole previous
 * calendar year); anything else is the year. A ticket dated outside the period, without date or
 * without id is ignored. Amounts and VAT amounts count only the values above zero.
 *
 * A report, a project or a client is the same whatever the case, the accents and the punctuation,
 * and it is named as it is written most often (the first in alphabetical order when several are
 * written as often). A label without any letter or digit (blank, or only punctuation) is no label:
 * the ticket is counted as missing. Groups come with most spending first, as it is shown (then
 * more tickets, then the first in alphabetical order of the label without case, accents and
 * punctuation), 20 at most with an exact count. The answer never depends on the order of the
 * tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class BusinessExpensesCalculator {

    public record Entry(
            UUID id,
            String merchant,
            BigDecimal amount,
            LocalDate date,
            boolean business,
            boolean reimbursable,
            boolean deductible,
            BigDecimal vat,
            String project,
            String client,
            String report
    ) {
    }

    /** The period that is looked at: its name and its first and last days. */
    public record Window(String period, LocalDate from, LocalDate to) {
    }

    public static final String MONTH = "month";
    public static final String QUARTER = "quarter";
    public static final String YEAR = "year";
    public static final String PREVIOUS_YEAR = "previous-year";
    public static final String DEFAULT_PERIOD = YEAR;

    static final int MAX_GROUPS = 20;
    static final int MAX_RECENT = 10;

    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    private static final Set<String> PERIODS = Set.of(MONTH, QUARTER, YEAR, PREVIOUS_YEAR);

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private BusinessExpensesCalculator() {
    }

    /** The period asked for, or the year when it is missing or not one of the four. */
    public static String normalizePeriod(String requested) {
        if (requested == null) {
            return DEFAULT_PERIOD;
        }

        String text = requested.trim().toLowerCase(Locale.ROOT);

        return PERIODS.contains(text) ? text : DEFAULT_PERIOD;
    }

    /** The first and the last day of the tickets that are looked at. */
    public static Window window(LocalDate today, String requestedPeriod) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        String period = normalizePeriod(requestedPeriod);

        if (MONTH.equals(period)) {
            return new Window(period, today.withDayOfMonth(1), today);
        }

        if (QUARTER.equals(period)) {
            return new Window(period, LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1), today);
        }

        if (PREVIOUS_YEAR.equals(period)) {
            return new Window(period, LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31));
        }

        return new Window(YEAR, LocalDate.of(today.getYear(), 1, 1), today);
    }

    public static BusinessExpensesResponse analyse(List<Entry> entries, LocalDate today, String requestedPeriod) {
        Window window = window(today, requestedPeriod);

        int allCount = 0;
        BigDecimal allAmount = BigDecimal.ZERO;
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        int deductibleCount = 0;
        BigDecimal deductibleAmount = BigDecimal.ZERO;
        BigDecimal deductibleVat = BigDecimal.ZERO;
        int reimbursableCount = 0;
        BigDecimal reimbursableAmount = BigDecimal.ZERO;
        List<Row> rows = new ArrayList<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(window.from()) || entry.date().isAfter(window.to())) {
                    continue;
                }

                BigDecimal amount = positive(entry.amount());

                allCount++;
                allAmount = allAmount.add(amount);

                if (!entry.business()) {
                    continue;
                }

                BigDecimal vat = positive(entry.vat());

                count++;
                total = total.add(amount);
                vatTotal = vatTotal.add(vat);

                if (entry.deductible()) {
                    deductibleCount++;
                    deductibleAmount = deductibleAmount.add(amount);
                    deductibleVat = deductibleVat.add(vat);
                }

                if (entry.reimbursable()) {
                    reimbursableCount++;
                    reimbursableAmount = reimbursableAmount.add(amount);
                }

                rows.add(new Row(
                        entry.id(),
                        merchantName(entry.merchant()),
                        entry.date(),
                        amount,
                        vat,
                        tidy(entry.project()),
                        tidy(entry.client()),
                        tidy(entry.report())
                ));
            }
        }

        BigDecimal share = allAmount.signum() == 0
                ? null
                : total.multiply(BigDecimal.valueOf(100)).divide(allAmount, 1, RoundingMode.HALF_UP);

        return new BusinessExpensesResponse(
                window.period(),
                window.from().toString(),
                window.to().toString(),
                count,
                money(total),
                allCount,
                money(allAmount),
                share,
                money(vatTotal),
                money(deductibleVat),
                deductibleCount,
                money(deductibleAmount),
                reimbursableCount,
                money(reimbursableAmount),
                section(rows, Row::report),
                section(rows, Row::project),
                section(rows, Row::client)
        );
    }

    private record Row(
            UUID id,
            String merchant,
            LocalDate date,
            BigDecimal amount,
            BigDecimal vat,
            String project,
            String client,
            String report
    ) {
    }

    private static final Comparator<Row> NEWEST_FIRST =
            Comparator.comparing((Row r) -> r.date(), Comparator.<LocalDate>reverseOrder())
                    .thenComparing((Row r) -> r.amount(), Comparator.<BigDecimal>reverseOrder())
                    .thenComparing((Row r) -> r.id().toString());

    private static final class Group {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        final List<Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;

        Group(String key) {
            this.key = key;
        }

        void add(String label, Row row) {
            spellings.merge(label, 1, Integer::sum);
            rows.add(row);
            total = total.add(row.amount());
            vat = vat.add(row.vat());
        }

        BusinessGroupResponse toResponse() {
            return new BusinessGroupResponse(key, mostUsed(spellings), rows.size(), money(total), money(vat), tickets(rows));
        }
    }

    private static BusinessSectionResponse section(List<Row> rows, Function<Row, String> labelOf) {
        Map<String, Group> groups = new HashMap<>();
        List<Row> missing = new ArrayList<>();
        BigDecimal missingAmount = BigDecimal.ZERO;

        for (Row row : rows) {
            String label = labelOf.apply(row);
            String key = key(label);

            if (key.isEmpty()) {
                missing.add(row);
                missingAmount = missingAmount.add(row.amount());
                continue;
            }

            groups.computeIfAbsent(key, Group::new).add(label, row);
        }

        List<Group> ordered = new ArrayList<>(groups.values());

        ordered.sort(
                Comparator.comparing((Group g) -> money(g.total), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Group g) -> g.rows.size(), Comparator.<Integer>reverseOrder())
                        .thenComparing((Group g) -> g.key)
        );

        List<BusinessGroupResponse> result = new ArrayList<>();

        for (int i = 0; i < ordered.size() && i < MAX_GROUPS; i++) {
            result.add(ordered.get(i).toResponse());
        }

        return new BusinessSectionResponse(ordered.size(), result, missing.size(), money(missingAmount), tickets(missing));
    }

    /** The latest tickets, the most recent first. */
    private static List<BusinessTicketResponse> tickets(List<Row> rows) {
        List<Row> sorted = new ArrayList<>(rows);

        sorted.sort(NEWEST_FIRST);

        List<BusinessTicketResponse> result = new ArrayList<>();

        for (int i = 0; i < sorted.size() && i < MAX_RECENT; i++) {
            Row row = sorted.get(i);

            result.add(new BusinessTicketResponse(row.id(), row.merchant(), row.date().toString(), money(row.amount())));
        }

        return result;
    }

    /** The merchant as written, or "Enseigne inconnue" when there is none or it is a placeholder. */
    private static String merchantName(String merchant) {
        String text = tidy(merchant);
        String key = key(text);

        return key.isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(key) ? UNKNOWN_MERCHANT : text;
    }

    private static BigDecimal positive(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    /** The spelling with the most votes, the first in alphabetical order on a tie. */
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
