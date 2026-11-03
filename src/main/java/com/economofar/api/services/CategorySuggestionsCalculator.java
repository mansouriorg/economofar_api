package com.economofar.api.services;

import com.economofar.api.dtos.CategorySuggestionGroupResponse;
import com.economofar.api.dtos.CategorySuggestionsResponse;

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
 * Suggests a category for the saved tickets that have none, from the tickets of the same merchant
 * that already have one.
 *
 * A ticket has no category when its category is empty or "Autre". The tickets looked at are the
 * ones dated from {@code months} months back from today (1 to 24, 12 by default) up to today; a
 * ticket dated after today, without date or without id is ignored. The categories are learned
 * from the tickets of the last 24 months.
 *
 * A merchant is the same whatever the case, the accents and the punctuation. An empty merchant
 * and the placeholders of the scan ("Commerçant inconnu", "Magasin inconnu", "Analyse en cours",
 * "Enseigne inconnue", "Inconnu") are not a merchant: such a ticket gets no suggestion. For a merchant, the category
 * suggested is the one that most of its categorized tickets have, provided that at least 2 of
 * them have it and that they are at least 75 % of the merchant's categorized tickets. The
 * category is written as it is written most often in the app (the first in alphabetical order
 * when several are written as often), and the same goes for the merchant's name.
 *
 * The tickets to fix are grouped by merchant, the biggest amount first, as it is shown (then the group with more
 * tickets, then the first in alphabetical order of the merchant without case, accents and
 * punctuation), 50 groups at most, and each group lists its first 200 tickets, the most recent
 * first (then the biggest amount, then the smallest id). Amounts count only the values above
 * zero. The answer never depends on the order of the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class CategorySuggestionsCalculator {

    public record Entry(UUID id, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int DEFAULT_MONTHS = 12;
    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;
    public static final int LEARNING_MONTHS = 24;

    static final int MIN_SUPPORT = 2;
    static final int MIN_AGREEMENT_PERCENT = 75;
    static final int MAX_GROUPS = 50;
    static final int MAX_TICKETS_PER_GROUP = 200;

    static final String OTHER_CATEGORY_KEY = "autre";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private CategorySuggestionsCalculator() {
    }

    public static int clampMonths(Integer requested) {
        if (requested == null) {
            return DEFAULT_MONTHS;
        }

        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    /** The first day of the tickets from which the categories are learned. */
    public static LocalDate learningStart(LocalDate today) {
        return today.minusMonths(LEARNING_MONTHS);
    }

    public static CategorySuggestionsResponse analyse(List<Entry> entries, LocalDate today, Integer requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);
        LocalDate learnStart = learningStart(today);
        LocalDate from = today.minusMonths(months);

        Map<String, Map<String, Integer>> categoriesOfMerchant = new HashMap<>();
        Map<String, Map<String, Integer>> spellingsOfCategory = new HashMap<>();
        List<Target> targets = new ArrayList<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(learnStart) || entry.date().isAfter(today)) {
                    continue;
                }

                String merchantText = tidy(entry.merchant());
                String merchantKey = key(merchantText);
                String categoryText = tidy(entry.category());
                String categoryKey = key(categoryText);

                boolean known = !merchantKey.isEmpty() && !UNKNOWN_MERCHANT_KEYS.contains(merchantKey);

                if (!categoryKey.isEmpty() && !categoryKey.equals(OTHER_CATEGORY_KEY)) {
                    spellingsOfCategory.computeIfAbsent(categoryKey, k -> new HashMap<>()).merge(categoryText, 1, Integer::sum);

                    if (known) {
                        categoriesOfMerchant.computeIfAbsent(merchantKey, k -> new HashMap<>()).merge(categoryKey, 1, Integer::sum);
                    }
                } else if (!entry.date().isBefore(from)) {
                    BigDecimal amount = entry.amount() != null && entry.amount().signum() > 0 ? entry.amount() : BigDecimal.ZERO;

                    targets.add(new Target(entry.id(), merchantKey, merchantText, amount, entry.date()));
                }
            }
        }

        Map<String, Suggestion> suggestions = new HashMap<>();

        for (Map.Entry<String, Map<String, Integer>> merchant : categoriesOfMerchant.entrySet()) {
            Suggestion suggestion = suggest(merchant.getValue());

            if (suggestion != null) {
                suggestions.put(merchant.getKey(), suggestion);
            }
        }

        int uncategorizedCount = 0;
        BigDecimal uncategorizedAmount = BigDecimal.ZERO;
        int suggestedCount = 0;
        BigDecimal suggestedAmount = BigDecimal.ZERO;
        Map<String, GroupBuilder> groups = new HashMap<>();

        for (Target target : targets) {
            uncategorizedCount++;
            uncategorizedAmount = uncategorizedAmount.add(target.amount());

            Suggestion suggestion = suggestions.get(target.merchantKey());

            if (suggestion == null) {
                continue;
            }

            suggestedCount++;
            suggestedAmount = suggestedAmount.add(target.amount());

            groups.computeIfAbsent(target.merchantKey(), k -> new GroupBuilder(k, suggestion)).add(target);
        }

        List<GroupBuilder> ordered = new ArrayList<>(groups.values());

        ordered.sort(
                Comparator.comparing((GroupBuilder g) -> money(g.amount), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((GroupBuilder g) -> g.targets.size(), Comparator.<Integer>reverseOrder())
                        .thenComparing((GroupBuilder g) -> g.merchantKey)
        );

        List<CategorySuggestionGroupResponse> result = new ArrayList<>();

        for (int i = 0; i < ordered.size() && i < MAX_GROUPS; i++) {
            result.add(ordered.get(i).toResponse(spellingsOfCategory));
        }

        return new CategorySuggestionsResponse(
                months,
                from.toString(),
                today.toString(),
                uncategorizedCount,
                money(uncategorizedAmount),
                suggestedCount,
                money(suggestedAmount),
                uncategorizedCount - suggestedCount,
                money(uncategorizedAmount).subtract(money(suggestedAmount)),
                ordered.size(),
                result
        );
    }

    private record Target(UUID id, String merchantKey, String merchantText, BigDecimal amount, LocalDate date) {
    }

    private record Suggestion(String categoryKey, int support, int total) {
    }

    private static Suggestion suggest(Map<String, Integer> counts) {
        int total = 0;
        int best = 0;
        String bestKey = null;

        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            total += count.getValue();

            if (count.getValue() > best) {
                best = count.getValue();
                bestKey = count.getKey();
            }
        }

        if (best >= MIN_SUPPORT && best * 100 >= MIN_AGREEMENT_PERCENT * total) {
            return new Suggestion(bestKey, best, total);
        }

        return null;
    }

    private static final class GroupBuilder {
        final String merchantKey;
        final Suggestion suggestion;
        final Map<String, Integer> spellings = new HashMap<>();
        final List<Target> targets = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;

        GroupBuilder(String merchantKey, Suggestion suggestion) {
            this.merchantKey = merchantKey;
            this.suggestion = suggestion;
        }

        void add(Target target) {
            spellings.merge(target.merchantText(), 1, Integer::sum);
            targets.add(target);
            amount = amount.add(target.amount());
        }

        CategorySuggestionGroupResponse toResponse(Map<String, Map<String, Integer>> spellingsOfCategory) {
            targets.sort(
                    Comparator.comparing((Target t) -> t.date(), Comparator.<LocalDate>reverseOrder())
                            .thenComparing((Target t) -> t.amount(), Comparator.<BigDecimal>reverseOrder())
                            .thenComparing((Target t) -> t.id().toString())
            );

            List<UUID> ids = new ArrayList<>();

            for (int i = 0; i < targets.size() && i < MAX_TICKETS_PER_GROUP; i++) {
                ids.add(targets.get(i).id());
            }

            return new CategorySuggestionGroupResponse(
                    mostUsed(spellings),
                    mostUsed(spellingsOfCategory.get(suggestion.categoryKey())),
                    BigDecimal.valueOf(suggestion.support() * 100L)
                            .divide(BigDecimal.valueOf(suggestion.total()), 1, RoundingMode.HALF_UP),
                    suggestion.support(),
                    suggestion.total(),
                    targets.size(),
                    money(amount),
                    ids
            );
        }
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
