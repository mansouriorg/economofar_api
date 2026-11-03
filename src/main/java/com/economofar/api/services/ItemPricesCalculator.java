package com.economofar.api.services;

import com.economofar.api.dtos.ItemPriceDetailResponse;
import com.economofar.api.dtos.ItemPriceMerchantResponse;
import com.economofar.api.dtos.ItemPricePointResponse;
import com.economofar.api.dtos.ItemPriceResponse;
import com.economofar.api.dtos.ItemPricesResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Follows the price of the articles that are on the saved tickets.
 *
 * An article is the name written on the ticket line, compared without capitals, accents, invisible
 * characters or extra spaces; a line named "Article" (the name the app gives to a line without name) is
 * ignored. The unit price of a line is its unit price when there is one above zero, otherwise its total
 * divided by its quantity (a quantity missing or below one counts for one), to the cent; a line without
 * a price above zero is ignored, as are the lines outside of the period. The amount counted for a line
 * is its unit price times its quantity.
 *
 * The period is the current month and the months before it: 12 by default, 3 at least and 24 at most.
 *
 * An article is followed when it was bought on two different days or in two named shops. The price of
 * a day is the amount spent that day divided by the quantity bought, to the cent. The change is the
 * price of the latest day against the price of the first day, in percent of the first price with one
 * decimal; the trend is rising or falling from 2 percent. A shop is named when the ticket has a shop
 * name that is not a placeholder ("Enseigne inconnue", "Commer\u00e7ant inconnu", "Magasin inconnu",
 * "Analyse en cours", "Inconnu") and has a letter or a digit; the average price in a shop is its amount
 * divided by its quantity,
 * to the cent, and the gap, the cheapest shops and the potential saving use these rounded averages. The
 * potential saving is what the lines of the shops that are above the cheapest average would have cost
 * less at that average.
 *
 * The answer never depends on the order of the lines. This class has no dependency on Spring so that it
 * can be tested alone.
 */
public final class ItemPricesCalculator {

    /** One line of a saved ticket. ticket tells which ticket it comes from, it can be null. */
    public record Line(
            String ticket,
            String name,
            String merchant,
            LocalDate date,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }

    public static final int DEFAULT_MONTHS = 12;
    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 24;
    public static final int MAX_KEY_LENGTH = 120;
    public static final int MAX_ITEMS = 100;
    public static final int MAX_MERCHANTS = 20;
    public static final int MAX_POINTS = 60;

    static final String TREND_RISING = "RISING";
    static final String TREND_FALLING = "FALLING";
    static final String TREND_STEADY = "STEADY";
    static final String TREND_NONE = "NONE";

    static final BigDecimal TREND_PERCENT = new BigDecimal("2.0");

    static final String PLACEHOLDER_ITEM = "article";

    /**
     * What a ticket gets in place of a shop name when the shop was not read, written with letters and
     * digits only, as the other analyses of the app do.
     */
    private static final Set<String> UNKNOWN_SHOP_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "enseigneinconnue",
            "inconnu"
    );

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Pattern MARKS = Pattern.compile("[\\p{M}\\p{Cf}]");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");

    private ItemPricesCalculator() {
    }

    // ---- period and names

    public static int clampMonths(int requested) {
        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    /** The first day of the period: the first day of the month, months - 1 months before today's month. */
    public static LocalDate periodStart(LocalDate today, int requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        return YearMonth.from(today).minusMonths(clampMonths(requestedMonths) - 1L).atDay(1);
    }

    /**
     * The way two names are compared: lowercased, without accents, invisible characters or extra spaces,
     * cut to 120 characters. An empty text is returned for a name that is missing. The key of a key is the
     * key itself.
     */
    public static String itemKey(String name) {
        if (name == null) {
            return "";
        }

        String text = name.toLowerCase(Locale.ROOT);
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = MARKS.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ").trim();
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        if (text.codePointCount(0, text.length()) > MAX_KEY_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_KEY_LENGTH)).trim();
        }

        return text;
    }

    /**
     * True for a shop name that is missing, has no letter or digit, or is only a placeholder such as
     * "Enseigne inconnue" or "Commer\u00e7ant inconnu" (capitals, accents and punctuation do not count).
     */
    static boolean isUnknownShop(String merchantKey) {
        StringBuilder letters = new StringBuilder();
        merchantKey.codePoints().filter(Character::isLetterOrDigit).forEach(letters::appendCodePoint);

        return letters.length() == 0 || UNKNOWN_SHOP_KEYS.contains(letters.toString());
    }

    /** A name the way it is shown: without invisible characters or extra spaces. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }

        return SPACES.matcher(INVISIBLE.matcher(name).replaceAll("")).replaceAll(" ").trim();
    }

    /** The unit price of a line to the cent, or null when the line has no price above zero. */
    static BigDecimal priceOf(Integer quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        BigDecimal price = null;

        if (unitPrice != null && unitPrice.signum() > 0) {
            price = money(unitPrice);
        } else if (totalPrice != null && totalPrice.signum() > 0) {
            int count = quantity == null || quantity <= 0 ? 1 : quantity;
            price = totalPrice.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        return price == null || price.signum() <= 0 ? null : price;
    }

    // ---- the answers

    public static ItemPricesResponse analyse(List<Line> lines, LocalDate today, int requestedMonths) {
        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        Map<String, List<Row>> groups = group(lines, from, today);

        Set<String> tickets = new HashSet<>();
        List<Data> tracked = new ArrayList<>();

        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            for (Row row : entry.getValue()) {
                if (row.ticket() != null) {
                    tickets.add(row.ticket());
                }
            }

            Data data = build(entry.getKey(), entry.getValue());

            if (data.tracked()) {
                tracked.add(data);
            }
        }

        tracked.sort(Comparator.comparing((Data d) -> d.item().spent()).reversed()
                .thenComparing(d -> d.item().key()));

        int rising = 0;
        int falling = 0;
        int steady = 0;
        int compareOnly = 0;
        int savingItems = 0;
        BigDecimal saving = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;

        for (Data data : tracked) {
            ItemPriceResponse item = data.item();

            switch (item.trend()) {
                case TREND_RISING -> rising++;
                case TREND_FALLING -> falling++;
                case TREND_STEADY -> steady++;
                default -> compareOnly++;
            }

            if (item.changePercent() != null) {
                weighted = weighted.add(item.changePercent().multiply(item.spent()));
                weight = weight.add(item.spent());
            }

            if (item.potentialSaving() != null && item.potentialSaving().signum() > 0) {
                savingItems++;
                saving = saving.add(item.potentialSaving());
            }
        }

        BigDecimal basket = weight.signum() > 0 ? weighted.divide(weight, 1, RoundingMode.HALF_UP) : null;

        List<ItemPriceResponse> items = new ArrayList<>();

        for (int i = 0; i < tracked.size() && i < MAX_ITEMS; i++) {
            items.add(tracked.get(i).item());
        }

        return new ItemPricesResponse(
                months,
                from.toString(),
                today.toString(),
                tickets.size(),
                groups.size(),
                tracked.size(),
                rising,
                falling,
                steady,
                compareOnly,
                groups.size() - tracked.size(),
                basket,
                money(saving),
                savingItems,
                tracked.size() - items.size(),
                items
        );
    }

    /**
     * The detail of one article, or null when the article is not followed over the period. The key is the
     * one of the list; a "+" of the key that arrives as a space is understood when only one article fits.
     */
    public static ItemPriceDetailResponse detail(List<Line> lines, String key, LocalDate today, int requestedMonths) {
        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        String wanted = itemKey(key);

        if (wanted.isEmpty()) {
            return null;
        }

        Map<String, List<Row>> groups = group(lines, from, today);
        String chosen = wanted;
        List<Row> rows = groups.get(chosen);

        if (rows == null) {
            // A plus sign that a web client sent as a space: the one article it stands for, if there is only one
            String match = null;
            int matches = 0;

            for (String candidate : groups.keySet()) {
                if (candidate.indexOf('+') >= 0 && itemKey(candidate.replace('+', ' ')).equals(wanted)) {
                    match = candidate;
                    matches++;
                }
            }

            if (matches == 1) {
                chosen = match;
                rows = groups.get(chosen);
            }
        }

        if (rows == null) {
            return null;
        }

        Data data = build(chosen, rows);

        if (!data.tracked()) {
            return null;
        }

        List<ItemPriceMerchantResponse> merchants = new ArrayList<>();

        for (int i = 0; i < data.merchants().size() && i < MAX_MERCHANTS; i++) {
            merchants.add(data.merchants().get(i));
        }

        int skipped = Math.max(data.points().size() - MAX_POINTS, 0);

        return new ItemPriceDetailResponse(
                months,
                data.item(),
                data.merchants().size(),
                merchants,
                data.points().size(),
                new ArrayList<>(data.points().subList(skipped, data.points().size()))
        );
    }

    // ---- lines to articles

    private record Row(
            String ticket,
            String name,
            String merchantKey,
            String merchantName,
            LocalDate date,
            BigDecimal price,
            int quantity
    ) {
        BigDecimal amount() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private record Data(
            ItemPriceResponse item,
            List<ItemPriceMerchantResponse> merchants,
            List<ItemPricePointResponse> points
    ) {
        boolean tracked() {
            return item.dayCount() >= 2 || item.merchantCount() >= 2;
        }
    }

    private static Map<String, List<Row>> group(List<Line> lines, LocalDate from, LocalDate today) {
        Map<String, List<Row>> groups = new HashMap<>();

        if (lines == null) {
            return groups;
        }

        for (Line line : lines) {
            if (line == null || line.date() == null || line.date().isBefore(from) || line.date().isAfter(today)) {
                continue;
            }

            String key = itemKey(line.name());

            if (key.isEmpty() || key.equals(PLACEHOLDER_ITEM)) {
                continue;
            }

            BigDecimal price = priceOf(line.quantity(), line.unitPrice(), line.totalPrice());

            if (price == null) {
                continue;
            }

            String merchantKey = itemKey(line.merchant());

            if (isUnknownShop(merchantKey)) {
                merchantKey = "";
            }

            int quantity = line.quantity() == null || line.quantity() <= 0 ? 1 : line.quantity();

            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(new Row(
                    line.ticket(),
                    cleanName(line.name()),
                    merchantKey,
                    cleanName(line.merchant()),
                    line.date(),
                    price,
                    quantity
            ));
        }

        return groups;
    }

    /** A running total of the amount and the quantity of a group of lines. */
    private static final class Total {
        BigDecimal amount = BigDecimal.ZERO;
        long quantity = 0;

        void add(Row row) {
            amount = amount.add(row.amount());
            quantity += row.quantity();
        }

        BigDecimal price() {
            return amount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }
    }

    private static Data build(String key, List<Row> rows) {
        BigDecimal spent = BigDecimal.ZERO;
        long quantity = 0;
        BigDecimal lowest = null;
        BigDecimal highest = null;

        TreeMap<LocalDate, Total> days = new TreeMap<>();
        Map<LocalDate, Set<String>> dayMerchants = new HashMap<>();
        Map<String, List<Row>> byMerchant = new HashMap<>();
        List<String> names = new ArrayList<>();

        for (Row row : rows) {
            spent = spent.add(row.amount());
            quantity += row.quantity();
            names.add(row.name());

            if (lowest == null || row.price().compareTo(lowest) < 0) {
                lowest = row.price();
            }

            if (highest == null || row.price().compareTo(highest) > 0) {
                highest = row.price();
            }

            days.computeIfAbsent(row.date(), d -> new Total()).add(row);

            if (!row.merchantKey().isEmpty()) {
                dayMerchants.computeIfAbsent(row.date(), d -> new HashSet<>()).add(row.merchantKey());
                byMerchant.computeIfAbsent(row.merchantKey(), k -> new ArrayList<>()).add(row);
            }
        }

        // ---- the shops
        List<Shop> shops = new ArrayList<>();

        for (Map.Entry<String, List<Row>> entry : byMerchant.entrySet()) {
            shops.add(shop(entry.getKey(), entry.getValue()));
        }

        shops.sort(Comparator.comparing((Shop s) -> s.average())
                .thenComparing(s -> s.name())
                .thenComparing(s -> s.key()));

        BigDecimal cheapest = shops.isEmpty() ? null : shops.get(0).average();
        BigDecimal dearest = shops.isEmpty() ? null : shops.get(shops.size() - 1).average();
        boolean compared = shops.size() >= 2;
        boolean gap = compared && dearest.compareTo(cheapest) > 0;

        BigDecimal saving = BigDecimal.ZERO;

        if (compared) {
            for (Shop shop : shops) {
                if (shop.average().compareTo(cheapest) > 0) {
                    for (Row row : shop.rows()) {
                        if (row.price().compareTo(cheapest) > 0) {
                            saving = saving.add(row.price().subtract(cheapest).multiply(BigDecimal.valueOf(row.quantity())));
                        }
                    }
                }
            }
        }

        List<ItemPriceMerchantResponse> merchants = new ArrayList<>();

        for (Shop shop : shops) {
            merchants.add(new ItemPriceMerchantResponse(
                    shop.name(),
                    shop.rows().size(),
                    shop.quantity(),
                    shop.average(),
                    shop.lastPrice(),
                    shop.lastDate().toString(),
                    gap && shop.average().compareTo(cheapest) == 0
            ));
        }

        // ---- the days
        List<ItemPricePointResponse> points = new ArrayList<>();
        Map<String, String> shopNames = new HashMap<>();

        for (Shop shop : shops) {
            shopNames.put(shop.key(), shop.name());
        }

        for (Map.Entry<LocalDate, Total> entry : days.entrySet()) {
            Set<String> used = dayMerchants.getOrDefault(entry.getKey(), Set.of());
            String merchant = used.size() == 1 ? shopNames.get(used.iterator().next()) : null;

            points.add(new ItemPricePointResponse(
                    entry.getKey().toString(),
                    entry.getValue().price(),
                    entry.getValue().quantity,
                    merchant,
                    used.size()
            ));
        }

        LocalDate firstDate = days.firstKey();
        LocalDate lastDate = days.lastKey();
        BigDecimal firstPrice = null;
        BigDecimal lastPrice = null;
        BigDecimal change = null;
        BigDecimal changePercent = null;
        String trend = TREND_NONE;

        if (days.size() >= 2) {
            firstPrice = days.get(firstDate).price();
            lastPrice = days.get(lastDate).price();
            change = lastPrice.subtract(firstPrice);
            changePercent = change.multiply(HUNDRED).divide(firstPrice, 1, RoundingMode.HALF_UP);

            if (changePercent.compareTo(TREND_PERCENT) >= 0) {
                trend = TREND_RISING;
            } else if (changePercent.compareTo(TREND_PERCENT.negate()) <= 0) {
                trend = TREND_FALLING;
            } else {
                trend = TREND_STEADY;
            }
        }

        ItemPriceResponse item = new ItemPriceResponse(
                key,
                mostFrequent(names),
                rows.size(),
                days.size(),
                quantity,
                money(spent),
                spent.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP),
                lowest,
                highest,
                firstDate.toString(),
                lastDate.toString(),
                firstPrice,
                lastPrice,
                change,
                changePercent,
                trend,
                shops.size(),
                gap ? shops.get(0).name() : null,
                gap ? cheapest : null,
                gap ? dearest.subtract(cheapest).multiply(HUNDRED).divide(cheapest, 1, RoundingMode.HALF_UP) : null,
                compared ? money(saving) : null
        );

        return new Data(item, merchants, points);
    }

    private record Shop(
            String key,
            String name,
            List<Row> rows,
            long quantity,
            BigDecimal average,
            BigDecimal lastPrice,
            LocalDate lastDate
    ) {
    }

    private static Shop shop(String key, List<Row> rows) {
        Total total = new Total();
        LocalDate lastDate = null;
        List<String> names = new ArrayList<>();

        for (Row row : rows) {
            total.add(row);
            names.add(row.merchantName());

            if (lastDate == null || row.date().isAfter(lastDate)) {
                lastDate = row.date();
            }
        }

        Total last = new Total();

        for (Row row : rows) {
            if (row.date().equals(lastDate)) {
                last.add(row);
            }
        }

        return new Shop(key, mostFrequent(names), rows, total.quantity, total.price(), last.price(), lastDate);
    }

    /** The spelling that is written most often; the smallest one when several are written as often. */
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

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
