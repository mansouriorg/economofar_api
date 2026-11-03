package com.economofar.api.services;

import com.economofar.api.dtos.PriceTrendResponse;
import com.economofar.api.dtos.ShoppingListItemResponse;
import com.economofar.api.dtos.ShoppingListResponse;
import com.economofar.api.dtos.ShoppingSuggestionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Prices the shopping list and proposes what to buy again.
 *
 * The prices come from the price tracker (the items of the saved tickets). A line of the list is
 * matched with a tracked item when every word of the line is found in the name of the tracked item
 * (accents, capitals, "oe" written as a ligature, a final "s", one-letter words and small words such as
 * "de", "du", "au" are ignored), so "lait" finds "LAIT DEMI ECREME 1L". When several tracked items fit,
 * the best one is chosen in this order: the exact name, a name that starts with a word of the line
 * ("lait demi-ecreme" before "chocolat au lait"), the fewest extra words, the most often bought, the
 * shortest name.
 *
 * A tracked item is proposed when it was bought on at least 3 different days over at least 14 days,
 * its usual delay between two purchases is 60 days or less, at least half of that delay has passed
 * since the last purchase and it has not been missing for much longer than its usual delay (an item no
 * longer bought is not proposed). It is marked "due" once 80 percent of the delay has passed. An item
 * is not proposed when a line of the list already stands for it: the line resolves to this tracked
 * item, or the line is more precise than a tracked name of at least two words.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class ShoppingListCalculator {

    public record Entry(Long id, String name, int quantity, boolean checked) {
    }

    private record Candidate(ShoppingSuggestionResponse suggestion, double ratio) {
    }

    private record Match(PriceTrendResponse trend, boolean exact, boolean leads, int extra) {
    }

    /**
     * The tracked items with their words, computed once.
     */
    private static final class Index {
        private final List<PriceTrendResponse> trends;
        private final List<Set<String>> words;

        private Index(List<PriceTrendResponse> prices) {
            this.trends = usable(prices);
            this.words = new ArrayList<>();

            for (PriceTrendResponse trend : trends) {
                words.add(ShoppingListCalculator.words(trend.name()));
            }
        }
    }

    public static final int MAX_ITEMS = 100;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;
    public static final int MAX_SUGGESTIONS = 10;

    static final int MIN_PURCHASES = 3;
    static final int MIN_SPAN_DAYS = 14;
    static final int MAX_INTERVAL_DAYS = 60;
    static final int ABANDONED_FACTOR = 3;
    static final int ABANDONED_MARGIN_DAYS = 14;

    private static final Set<String> SMALL_WORDS = Set.of(
            "de", "du", "des", "la", "le", "les", "au", "aux", "un", "une", "et", "en"
    );

    private ShoppingListCalculator() {
    }

    // ---------------------------------------------------------------- input rules

    /**
     * Single spaces, no spaces around. Refuses an empty name, a name over 100 characters and a name
     * without any real word.
     */
    public static String cleanName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Indiquez le nom de l'article.");
        }

        String text = raw.replaceAll("(?U)\\s+", " ").trim();

        if (text.isEmpty()) {
            throw new IllegalArgumentException("Indiquez le nom de l'article.");
        }

        if (text.codePointCount(0, text.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Le nom de l'article est trop long (100 caractères maximum).");
        }

        if (words(text).isEmpty()) {
            throw new IllegalArgumentException("Le nom de l'article n'est pas valide.");
        }

        return text;
    }

    /**
     * 1 when missing, otherwise the quantity itself when it is between 1 and 99.
     */
    public static int checkQuantity(Integer raw) {
        if (raw == null) {
            return MIN_QUANTITY;
        }

        if (raw < MIN_QUANTITY || raw > MAX_QUANTITY) {
            throw new IllegalArgumentException("La quantité doit être comprise entre 1 et 99.");
        }

        return raw;
    }

    /**
     * True when both names are the same item (capitals, accents, punctuation, a final "s" and small
     * words ignored).
     */
    public static boolean sameItem(String first, String second) {
        Set<String> left = words(first);

        return !left.isEmpty() && left.equals(words(second));
    }

    // ---------------------------------------------------------------- the list

    public static ShoppingListResponse build(
            List<Entry> entries,
            List<PriceTrendResponse> prices,
            LocalDate today
    ) {
        List<Entry> ordered = new ArrayList<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry != null && entry.name() != null) {
                    ordered.add(entry);
                }
            }
        }

        ordered.sort(
                Comparator.comparing(Entry::checked)
                        .thenComparing(Entry::id, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        Index index = new Index(prices);

        List<ShoppingListItemResponse> items = new ArrayList<>();
        BigDecimal estimatedTotal = BigDecimal.ZERO;
        BigDecimal potentialSavings = BigDecimal.ZERO;
        int priced = 0;
        int unpriced = 0;
        int checked = 0;

        for (Entry entry : ordered) {
            int quantity = Math.max(MIN_QUANTITY, entry.quantity());
            BigDecimal count = BigDecimal.valueOf(quantity);

            PriceTrendResponse trend = findPrice(entry.name(), index);

            BigDecimal unitPrice = null;
            boolean lastPriceUsed = false;

            if (trend != null) {
                if (positive(trend.lastPrice())) {
                    unitPrice = money(trend.lastPrice());
                    lastPriceUsed = true;
                } else if (positive(trend.averagePrice())) {
                    unitPrice = money(trend.averagePrice());
                }
            }

            BigDecimal lineTotal = unitPrice == null ? null : money(unitPrice.multiply(count));

            String cheaperMerchant = null;
            BigDecimal cheaperPrice = null;

            if (lastPriceUsed
                    && positive(trend.cheaperPrice())
                    && trend.cheaperMerchant() != null
                    && !trend.cheaperMerchant().isBlank()
                    && money(trend.cheaperPrice()).compareTo(unitPrice) < 0) {
                cheaperMerchant = trend.cheaperMerchant();
                cheaperPrice = money(trend.cheaperPrice());
            }

            if (entry.checked()) {
                checked++;
            } else if (unitPrice == null) {
                unpriced++;
            } else {
                priced++;
                estimatedTotal = estimatedTotal.add(lineTotal);

                if (cheaperPrice != null) {
                    potentialSavings = potentialSavings.add(unitPrice.subtract(cheaperPrice).multiply(count));
                }
            }

            items.add(new ShoppingListItemResponse(
                    entry.id(),
                    entry.name(),
                    quantity,
                    entry.checked(),
                    unitPrice,
                    lineTotal,
                    trend == null ? null : trend.lastMerchant(),
                    cheaperMerchant,
                    cheaperPrice,
                    trend == null ? null : trend.name()
            ));
        }

        return new ShoppingListResponse(
                items,
                money(estimatedTotal),
                money(potentialSavings),
                priced,
                unpriced,
                checked,
                ordered.size(),
                suggest(ordered, index, today)
        );
    }

    // ---------------------------------------------------------------- suggestions

    public static List<ShoppingSuggestionResponse> suggest(
            List<Entry> entries,
            List<PriceTrendResponse> prices,
            LocalDate today
    ) {
        return suggest(entries, new Index(prices), today);
    }

    private static List<ShoppingSuggestionResponse> suggest(List<Entry> entries, Index index, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();

        if (today == null) {
            return new ArrayList<>();
        }

        Set<PriceTrendResponse> covered = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Set<String>> lines = new ArrayList<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.name() == null || PriceTrendDetector.itemKey(entry.name()) == null) {
                    continue;
                }

                PriceTrendResponse resolved = findPrice(entry.name(), index);

                if (resolved != null) {
                    covered.add(resolved);
                }

                Set<String> lineWords = words(entry.name());

                if (!lineWords.isEmpty()) {
                    lines.add(lineWords);
                }
            }
        }

        for (int position = 0; position < index.trends.size(); position++) {
            PriceTrendResponse trend = index.trends.get(position);

            if (trend.purchaseCount() < MIN_PURCHASES || PriceTrendDetector.itemKey(trend.name()) == null) {
                continue;
            }

            LocalDate first = parseDay(trend.firstDate());
            LocalDate last = parseDay(trend.lastDate());

            if (first == null || last == null) {
                continue;
            }

            long span = ChronoUnit.DAYS.between(first, last);

            if (span < MIN_SPAN_DAYS) {
                continue;
            }

            long interval = Math.max(1L, Math.round((double) span / (trend.purchaseCount() - 1)));

            if (interval > MAX_INTERVAL_DAYS) {
                continue;
            }

            long since = Math.max(0L, ChronoUnit.DAYS.between(last, today));

            if (since * 2 < interval) {
                continue;
            }

            if (since > Math.max(ABANDONED_FACTOR * interval, interval + ABANDONED_MARGIN_DAYS)) {
                continue;
            }

            if (isOnTheList(trend, index.words.get(position), covered, lines)) {
                continue;
            }

            boolean due = since * 5 >= interval * 4;

            candidates.add(new Candidate(
                    new ShoppingSuggestionResponse(
                            trend.name(),
                            trend.purchaseCount(),
                            (int) interval,
                            (int) Math.min(since, 36500L),
                            due,
                            trend.lastPrice() == null ? null : money(trend.lastPrice()),
                            trend.lastMerchant()
                    ),
                    (double) since / interval
            ));
        }

        candidates.sort(
                Comparator.comparing((Candidate candidate) -> !candidate.suggestion().due())
                        .thenComparing(Comparator.comparingDouble(Candidate::ratio).reversed())
                        .thenComparing(
                                Comparator.comparingInt((Candidate candidate) -> candidate.suggestion().purchaseCount()).reversed()
                        )
                        .thenComparing(candidate -> candidate.suggestion().name(), String.CASE_INSENSITIVE_ORDER)
        );

        List<ShoppingSuggestionResponse> result = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (result.size() >= MAX_SUGGESTIONS) {
                break;
            }

            result.add(candidate.suggestion());
        }

        return result;
    }

    private static boolean isOnTheList(
            PriceTrendResponse trend,
            Set<String> have,
            Set<PriceTrendResponse> covered,
            List<Set<String>> lines
    ) {
        if (covered.contains(trend)) {
            return true;
        }

        if (have.size() < 2) {
            return false;
        }

        for (Set<String> line : lines) {
            if (line.containsAll(have)) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- matching

    /**
     * The tracked item that gives the price of a list line, or null.
     */
    private static PriceTrendResponse findPrice(String name, Index index) {
        if (PriceTrendDetector.itemKey(name) == null) {
            return null;
        }

        Set<String> wanted = words(name);

        if (wanted.isEmpty()) {
            return null;
        }

        Match best = null;

        for (int position = 0; position < index.trends.size(); position++) {
            Set<String> have = index.words.get(position);

            if (have.isEmpty() || !have.containsAll(wanted)) {
                continue;
            }

            Match candidate = new Match(
                    index.trends.get(position),
                    have.equals(wanted),
                    wanted.contains(have.iterator().next()),
                    have.size() - wanted.size()
            );

            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best == null ? null : best.trend();
    }

    private static boolean isBetter(Match candidate, Match best) {
        if (candidate.exact() != best.exact()) {
            return candidate.exact();
        }

        if (candidate.leads() != best.leads()) {
            return candidate.leads();
        }

        if (candidate.extra() != best.extra()) {
            return candidate.extra() < best.extra();
        }

        PriceTrendResponse trend = candidate.trend();
        PriceTrendResponse other = best.trend();

        if (trend.purchaseCount() != other.purchaseCount()) {
            return trend.purchaseCount() > other.purchaseCount();
        }

        if (trend.name().length() != other.name().length()) {
            return trend.name().length() < other.name().length();
        }

        return trend.name().compareTo(other.name()) < 0;
    }

    private static Set<String> words(String raw) {
        Set<String> result = new LinkedHashSet<>();
        String key = PriceTrendDetector.normalize(raw);

        if (key == null) {
            return result;
        }

        key = key.replace("œ", "oe").replace("æ", "ae");

        for (String part : key.split(" ")) {
            if (part.isEmpty() || SMALL_WORDS.contains(part)) {
                continue;
            }

            if (part.length() == 1 && Character.isLetter(part.charAt(0))) {
                continue;
            }

            result.add(part.length() > 3 && part.endsWith("s") ? part.substring(0, part.length() - 1) : part);
        }

        return result;
    }

    // ---------------------------------------------------------------- small helpers

    private static List<PriceTrendResponse> usable(List<PriceTrendResponse> prices) {
        List<PriceTrendResponse> result = new ArrayList<>();

        if (prices != null) {
            for (PriceTrendResponse price : prices) {
                if (price != null && price.name() != null && !price.name().isBlank()) {
                    result.add(price);
                }
            }
        }

        return result;
    }

    private static LocalDate parseDay(String value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
