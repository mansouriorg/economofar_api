package com.economofar.api.services;

import com.economofar.api.dtos.PricePointResponse;
import com.economofar.api.dtos.PriceTrendResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Follows the price of the same item across several purchases.
 *
 * Items are grouped by name (not case sensitive, accents and punctuation ignored). An item is
 * kept when it was bought on at least 2 different days. If the item was bought in several shops
 * on the same day, that day uses the price of the last shop in alphabetical order.
 *
 * For each item the class gives the first, previous, last, lowest, highest and average price,
 * the change in percent between the first and the last price, a short history and, when another
 * shop recently sold the same item at least 5 percent cheaper than the last purchase, that shop
 * and its price.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class PriceTrendDetector {

    public record Observation(String itemName, String merchant, BigDecimal unitPrice, LocalDate date) {
    }

    static final int MAX_RESULTS = 100;
    static final int MAX_HISTORY = 12;
    static final int MIN_DAYS = 2;
    static final int CHEAPER_WINDOW_DAYS = 180;
    static final BigDecimal STABLE_LIMIT = new BigDecimal("3");
    static final BigDecimal CHEAPER_FACTOR = new BigDecimal("0.95");

    private static final Set<String> GENERIC_NAMES = Set.of(
            "article", "articles", "produit", "produits", "item", "items", "divers", "autre",
            "autres", "total", "sous total", "tva", "remise", "reduction", "monnaie", "espece",
            "especes", "carte", "cb", "ticket"
    );

    private record Day(LocalDate date, BigDecimal price, String merchant) {
    }

    private record MerchantLatest(String name, LocalDate date, BigDecimal sum, int count) {
        BigDecimal average() {
            return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }

    private PriceTrendDetector() {
    }

    public static List<PriceTrendResponse> analyze(List<Observation> observations) {
        Map<String, List<Observation>> groups = new LinkedHashMap<>();

        if (observations != null) {
            for (Observation observation : observations) {
                if (observation == null
                        || observation.date() == null
                        || observation.unitPrice() == null
                        || observation.unitPrice().setScale(2, RoundingMode.HALF_UP).signum() <= 0) {
                    continue;
                }

                String key = itemKey(observation.itemName());

                if (key == null) {
                    continue;
                }

                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(observation);
            }
        }

        List<PriceTrendResponse> results = new ArrayList<>();

        for (List<Observation> group : groups.values()) {
            PriceTrendResponse trend = buildTrend(group);

            if (trend != null) {
                results.add(trend);
            }
        }

        results.sort(
                Comparator.comparingInt(PriceTrendResponse::purchaseCount).reversed()
                        .thenComparing(item -> item.name().toLowerCase(Locale.ROOT))
        );

        if (results.size() > MAX_RESULTS) {
            return new ArrayList<>(results.subList(0, MAX_RESULTS));
        }

        return results;
    }

    private static PriceTrendResponse buildTrend(List<Observation> group) {
        List<Observation> sorted = new ArrayList<>(group);
        sorted.sort(
                Comparator.comparing(Observation::date)
                        .thenComparing(observation -> merchantSortKey(observation.merchant()))
                        .thenComparing(Observation::unitPrice)
        );

        List<Day> days = new ArrayList<>();
        Map<String, Integer> spellingCount = new LinkedHashMap<>();
        Map<String, Integer> spellingLastSeen = new LinkedHashMap<>();
        Map<String, MerchantLatest> latestByMerchant = new TreeMap<>();

        int position = 0;
        int index = 0;

        while (index < sorted.size()) {
            LocalDate date = sorted.get(index).date();
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            String merchant = null;
            String dayMerchantKey = null;

            while (index < sorted.size() && sorted.get(index).date().equals(date)) {
                Observation observation = sorted.get(index);

                String sortKey = merchantSortKey(observation.merchant());

                if (!sortKey.equals(dayMerchantKey)) {
                    dayMerchantKey = sortKey;
                    sum = BigDecimal.ZERO;
                    count = 0;
                }

                sum = sum.add(observation.unitPrice());
                count++;
                merchant = observation.merchant();

                String spelling = display(observation.itemName());
                spellingCount.merge(spelling, 1, Integer::sum);
                spellingLastSeen.put(spelling, position);
                position++;

                String merchantKey = normalize(observation.merchant());

                if (merchantKey != null) {
                    MerchantLatest current = latestByMerchant.get(merchantKey);

                    if (current == null || !current.date().equals(date)) {
                        latestByMerchant.put(
                                merchantKey,
                                new MerchantLatest(display(observation.merchant()), date, observation.unitPrice(), 1)
                        );
                    } else {
                        latestByMerchant.put(
                                merchantKey,
                                new MerchantLatest(current.name(), date, current.sum().add(observation.unitPrice()), current.count() + 1)
                        );
                    }
                }

                index++;
            }

            BigDecimal dayPrice = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

            if (dayPrice.signum() > 0) {
                days.add(new Day(date, dayPrice, cleanMerchant(merchant)));
            }
        }

        if (days.size() < MIN_DAYS) {
            return null;
        }

        int size = days.size();
        Day firstDay = days.get(0);
        Day lastDay = days.get(size - 1);
        BigDecimal first = firstDay.price();
        BigDecimal previous = days.get(size - 2).price();
        BigDecimal last = lastDay.price();

        BigDecimal min = first;
        BigDecimal max = first;
        BigDecimal total = BigDecimal.ZERO;

        for (Day day : days) {
            min = min.min(day.price());
            max = max.max(day.price());
            total = total.add(day.price());
        }

        BigDecimal average = total.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);

        BigDecimal exactChange = last.subtract(first)
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 6, RoundingMode.HALF_UP);

        BigDecimal change = exactChange.setScale(1, RoundingMode.HALF_UP);

        String trend;

        if (exactChange.abs().compareTo(STABLE_LIMIT) < 0) {
            trend = "stable";
        } else if (exactChange.signum() > 0) {
            trend = "up";
        } else {
            trend = "down";
        }

        String lastMerchantKey = normalize(lastDay.merchant());
        LocalDate oldest = lastDay.date().minusDays(CHEAPER_WINDOW_DAYS);
        BigDecimal threshold = last.multiply(CHEAPER_FACTOR);
        String cheaperMerchant = null;
        BigDecimal cheaperPrice = null;

        for (Map.Entry<String, MerchantLatest> entry : latestByMerchant.entrySet()) {
            if (entry.getKey().equals(lastMerchantKey)) {
                continue;
            }

            MerchantLatest candidate = entry.getValue();

            if (candidate.date().isBefore(oldest)) {
                continue;
            }

            BigDecimal price = candidate.average();

            if (price.signum() <= 0 || price.compareTo(threshold) > 0) {
                continue;
            }

            if (cheaperPrice == null || price.compareTo(cheaperPrice) < 0) {
                cheaperMerchant = candidate.name();
                cheaperPrice = price;
            }
        }

        List<PricePointResponse> history = new ArrayList<>();

        for (Day day : days.subList(Math.max(0, size - MAX_HISTORY), size)) {
            history.add(new PricePointResponse(day.date().toString(), day.price(), day.merchant()));
        }

        return new PriceTrendResponse(
                bestSpelling(spellingCount, spellingLastSeen),
                size,
                firstDay.date().toString(),
                lastDay.date().toString(),
                first,
                previous,
                last,
                min,
                max,
                average,
                change,
                trend,
                lastDay.merchant(),
                cheaperMerchant,
                cheaperPrice,
                history
        );
    }

    private static String bestSpelling(Map<String, Integer> counts, Map<String, Integer> lastSeen) {
        String best = null;

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (best == null) {
                best = entry.getKey();
                continue;
            }

            int bestCount = counts.get(best);
            int count = entry.getValue();

            if (count > bestCount
                    || (count == bestCount && lastSeen.get(entry.getKey()) > lastSeen.get(best))) {
                best = entry.getKey();
            }
        }

        return best;
    }

    /**
     * Lower case, no accents, letters and digits only, single spaces. Null when empty.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();

        return text.isEmpty() ? null : text;
    }

    /**
     * Key used to group the same item. Null for generic or meaningless names.
     */
    static String itemKey(String raw) {
        String key = normalize(raw);

        if (key == null || GENERIC_NAMES.contains(key)) {
            return null;
        }

        int letters = 0;

        for (int i = 0; i < key.length(); i++) {
            if (Character.isLetter(key.charAt(i))) {
                letters++;
            }
        }

        return letters < 2 ? null : key;
    }

    private static String merchantSortKey(String merchant) {
        String key = normalize(merchant);
        return key == null ? "" : key;
    }

    private static String cleanMerchant(String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return null;
        }

        return display(merchant);
    }

    /**
     * Tidy name for the screen: single spaces, and receipts written in capitals become
     * "Lait demi-ecreme" instead of "LAIT DEMI-ECREME".
     */
    static String display(String raw) {
        String text = raw.trim().replaceAll("\\s+", " ");

        boolean hasLetter = false;
        boolean hasLower = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.isLetter(c)) {
                hasLetter = true;

                if (Character.isLowerCase(c)) {
                    hasLower = true;
                }
            }
        }

        if (hasLetter && !hasLower && text.length() > 1) {
            String lower = text.toLowerCase(Locale.ROOT);
            return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
        }

        return text;
    }
}
