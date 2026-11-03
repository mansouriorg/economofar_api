package com.economofar.api.services;

import com.economofar.api.dtos.ShopCategoryResponse;
import com.economofar.api.dtos.ShopDetailResponse;
import com.economofar.api.dtos.ShopMonthResponse;
import com.economofar.api.dtos.ShopResponse;
import com.economofar.api.dtos.ShopTicketResponse;
import com.economofar.api.dtos.ShopsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Tells where the person spends: the shops of the saved tickets of the last months, with their spending,
 * visits, average ticket and rhythm, and what changed lately.
 *
 * A ticket is a row. Its amount counts to the cent when it is above zero, and as zero otherwise (a row
 * with no amount is still a visit). A row with no date, in the future or before the history is ignored.
 * The shop of a ticket is its name compared without capitals, accents, invisible characters, punctuation
 * or extra spaces (the key, 120 characters at most). A name that is missing, has no letter or digit, or
 * is only a placeholder ("Enseigne inconnue", "Commer\u00e7ant inconnu", "Magasin inconnu", "Analyse en
 * cours", "Inconnu") is not a shop: those tickets only count in the totals and in unknownSpent.
 *
 * The period is the current month and the months before it: 12 by default, 3 at least and 24 at most. The
 * figures of a shop are about the period, and only the shops with a ticket in the period are listed. What
 * changed lately looks at the last 90 days (today and the 89 days before) against the 90 days before them,
 * whatever the period, in a history of 24 months. A shop is new when its first ticket of the history is
 * within the last 90 days, which is only said when tickets were saved on 3 different days at least before
 * the last 90 days (the baseline): a person who has just started has no new shop.
 *
 * The answer never depends on the order of the rows. This class has no dependency on Spring so that it can
 * be tested alone.
 */
public final class ShopsCalculator {

    /** One saved ticket. ticket is its id and can be null, merchant and category can be null. */
    public record Row(String ticket, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int DEFAULT_MONTHS = 12;
    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 24;
    public static final int HISTORY_MONTHS = 24;
    public static final int BASELINE_MIN_DAYS = 3;
    public static final int RECENT_DAYS = 90;
    public static final int MAX_KEY_LENGTH = 120;
    public static final int MAX_SHOPS = 100;
    public static final int MAX_CATEGORIES = 10;
    public static final int MAX_TICKETS = 20;

    static final String STATUS_NEW = "NEW";
    static final String STATUS_FADING = "FADING";
    static final String STATUS_UP = "UP";
    static final String STATUS_DOWN = "DOWN";
    static final String STATUS_STEADY = "STEADY";

    static final String OTHER_CATEGORY = "Autre";
    static final int FADING_MIN_VISIT_DAYS = 2;
    static final BigDecimal MIN_CHANGE = new BigDecimal("10.00");

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
    private static final Pattern APOSTROPHES = Pattern.compile("['\u2018\u2019\u02bc]");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");

    private ShopsCalculator() {
    }

    /** A row once read: key is empty for a ticket that has no shop. */
    private record Item(String ticket, String key, String name, String category, BigDecimal amount, LocalDate date) {
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

    /** The first day of the tickets to read: the period, or the last 24 months when the period is shorter. */
    public static LocalDate historyStart(LocalDate today, int requestedMonths) {
        return periodStart(today, Math.max(clampMonths(requestedMonths), HISTORY_MONTHS));
    }

    /**
     * The way two names are compared: lowercased, without accents, invisible characters or apostrophes,
     * every run of characters that are not letters or digits turned into one space, cut to 120 characters.
     * An empty text is returned for a name that is missing. The key of a key is the key itself.
     */
    public static String shopKey(String name) {
        if (name == null) {
            return "";
        }

        String text = name.toLowerCase(Locale.ROOT);
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = MARKS.matcher(text).replaceAll("");
        text = APOSTROPHES.matcher(text).replaceAll("");
        text = NOT_ALPHANUMERIC.matcher(text).replaceAll(" ").trim();
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        if (text.codePointCount(0, text.length()) > MAX_KEY_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_KEY_LENGTH)).trim();
        }

        return text;
    }

    /**
     * True for a shop key that is empty or only a placeholder such as "enseigne inconnue" (spaces do
     * not count).
     */
    static boolean isUnknownShop(String key) {
        StringBuilder letters = new StringBuilder();
        key.codePoints().filter(ShopsCalculator::isLetterOrNumber).forEach(letters::appendCodePoint);

        return letters.length() == 0 || UNKNOWN_SHOP_KEYS.contains(letters.toString());
    }

    /** A letter or a number of any kind: what a key is made of besides its spaces. */
    private static boolean isLetterOrNumber(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
                 Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true;
            default -> false;
        };
    }

    /** A name the way it is shown: without invisible characters or extra spaces. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }

        return SPACES.matcher(INVISIBLE.matcher(name).replaceAll("")).replaceAll(" ").trim();
    }

    // ---- the answers

    public static ShopsResponse overview(List<Row> rows, LocalDate today, int requestedMonths) {
        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        List<Item> history = read(rows, historyStart(today, months), today);
        Map<String, List<Item>> shops = byShop(history);
        boolean baseline = hasBaseline(history, today);

        BigDecimal totalSpent = BigDecimal.ZERO;
        int ticketCount = 0;
        BigDecimal unknownSpent = BigDecimal.ZERO;
        int unknownCount = 0;

        for (Item item : history) {
            if (item.date().isBefore(from)) {
                continue;
            }

            totalSpent = totalSpent.add(item.amount());
            ticketCount++;

            if (item.key().isEmpty()) {
                unknownSpent = unknownSpent.add(item.amount());
                unknownCount++;
            }
        }

        List<ShopResponse> lines = new ArrayList<>();

        for (Map.Entry<String, List<Item>> shop : shops.entrySet()) {
            ShopResponse line = line(shop.getKey(), shop.getValue(), from, today, totalSpent, baseline);

            if (line != null) {
                lines.add(line);
            }
        }

        lines.sort(Comparator
                .comparing(ShopResponse::spent, Comparator.reverseOrder())
                .thenComparing(ShopResponse::ticketCount, Comparator.reverseOrder())
                .thenComparing(ShopResponse::key));

        int newCount = 0;
        int fadingCount = 0;
        int upCount = 0;
        int downCount = 0;
        BigDecimal topSpent = BigDecimal.ZERO;

        for (int i = 0; i < lines.size(); i++) {
            ShopResponse line = lines.get(i);

            if (i < 3) {
                topSpent = topSpent.add(line.spent());
            }

            switch (line.status()) {
                case STATUS_NEW -> newCount++;
                case STATUS_FADING -> fadingCount++;
                case STATUS_UP -> upCount++;
                case STATUS_DOWN -> downCount++;
                default -> {
                }
            }
        }

        BigDecimal top = totalSpent.signum() > 0 && !lines.isEmpty() ? percent(topSpent, totalSpent) : null;
        List<ShopResponse> listed = lines.size() > MAX_SHOPS ? new ArrayList<>(lines.subList(0, MAX_SHOPS)) : lines;

        return new ShopsResponse(
                months,
                from.toString(),
                today.toString(),
                money(totalSpent),
                ticketCount,
                lines.size(),
                money(unknownSpent),
                unknownCount,
                top,
                baseline,
                newCount,
                fadingCount,
                upCount,
                downCount,
                lines.size() - listed.size(),
                listed
        );
    }

    /** One shop of the period, or null when the key is not a shop or the shop has no ticket in the period. */
    public static ShopDetailResponse detail(List<Row> rows, String key, LocalDate today, int requestedMonths) {
        String wanted = shopKey(key);

        if (isUnknownShop(wanted)) {
            return null;
        }

        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        List<Item> history = read(rows, historyStart(today, months), today);
        List<Item> own = byShop(history).get(wanted);

        if (own == null) {
            return null;
        }

        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal namedSpent = BigDecimal.ZERO;
        int namedCount = 0;

        for (Item item : history) {
            if (!item.date().isBefore(from)) {
                totalSpent = totalSpent.add(item.amount());

                if (!item.key().isEmpty()) {
                    namedSpent = namedSpent.add(item.amount());
                    namedCount++;
                }
            }
        }

        ShopResponse shop = line(wanted, own, from, today, totalSpent, hasBaseline(history, today));

        if (shop == null) {
            return null;
        }

        List<Item> period = new ArrayList<>();

        for (Item item : own) {
            if (!item.date().isBefore(from)) {
                period.add(item);
            }
        }

        BigDecimal overall = namedCount == 0 ? null : divide(namedSpent, namedCount, 2);
        BigDecimal gap = null;

        if (overall != null && overall.signum() > 0) {
            gap = shop.averageBasket().subtract(overall).multiply(HUNDRED).divide(overall, 1, RoundingMode.HALF_UP);
        }

        // every month of the period
        Map<YearMonth, BigDecimal> monthSpent = new TreeMap<>();
        Map<YearMonth, Integer> monthCount = new HashMap<>();

        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(today)); month = month.plusMonths(1)) {
            monthSpent.put(month, BigDecimal.ZERO);
            monthCount.put(month, 0);
        }

        for (Item item : period) {
            YearMonth month = YearMonth.from(item.date());
            monthSpent.merge(month, item.amount(), BigDecimal::add);
            monthCount.merge(month, 1, Integer::sum);
        }

        List<ShopMonthResponse> monthly = new ArrayList<>();

        for (Map.Entry<YearMonth, BigDecimal> entry : monthSpent.entrySet()) {
            monthly.add(new ShopMonthResponse(entry.getKey().toString(), money(entry.getValue()), monthCount.get(entry.getKey())));
        }

        // categories
        Map<String, List<Item>> groups = new TreeMap<>();

        for (Item item : period) {
            groups.computeIfAbsent(shopKey(categoryName(item.category())), k -> new ArrayList<>()).add(item);
        }

        List<ShopCategoryResponse> categories = new ArrayList<>();
        BigDecimal shopSpent = shop.spent();

        for (List<Item> group : groups.values()) {
            List<String> names = new ArrayList<>();
            BigDecimal spent = BigDecimal.ZERO;

            for (Item item : group) {
                names.add(categoryName(item.category()));
                spent = spent.add(item.amount());
            }

            categories.add(new ShopCategoryResponse(
                    mostFrequent(names),
                    money(spent),
                    group.size(),
                    shopSpent.signum() > 0 ? percent(spent, shopSpent) : null
            ));
        }

        categories.sort(Comparator
                .comparing(ShopCategoryResponse::spent, Comparator.reverseOrder())
                .thenComparing(ShopCategoryResponse::ticketCount, Comparator.reverseOrder())
                .thenComparing(ShopCategoryResponse::name));

        int categoryTotal = categories.size();

        if (categories.size() > MAX_CATEGORIES) {
            categories = new ArrayList<>(categories.subList(0, MAX_CATEGORIES));
        }

        // days of the week
        int[] weekdays = new int[7];
        Set<LocalDate> days = new HashSet<>();

        for (Item item : period) {
            if (days.add(item.date())) {
                weekdays[item.date().getDayOfWeek().getValue() - 1]++;
            }
        }

        List<Integer> weekdayVisitDays = new ArrayList<>();

        for (int count : weekdays) {
            weekdayVisitDays.add(count);
        }

        // tickets
        List<Item> latest = new ArrayList<>(period);
        latest.sort(Comparator
                .comparing(Item::date, Comparator.reverseOrder())
                .thenComparing(Item::amount, Comparator.reverseOrder())
                .thenComparing(item -> item.ticket() == null ? "" : item.ticket())
                .thenComparing(item -> categoryName(item.category())));

        List<ShopTicketResponse> tickets = new ArrayList<>();

        for (Item item : latest.subList(0, Math.min(MAX_TICKETS, latest.size()))) {
            tickets.add(new ShopTicketResponse(
                    item.ticket() == null ? "" : item.ticket(),
                    item.date().toString(),
                    categoryName(item.category()),
                    money(item.amount())
            ));
        }

        return new ShopDetailResponse(
                months,
                from.toString(),
                today.toString(),
                shop,
                overall,
                gap,
                monthly,
                categoryTotal,
                categories,
                weekdayVisitDays,
                latest.size() - tickets.size(),
                tickets
        );
    }

    /** A category the way it is shown: cleaned, or "Autre" when it has no letter or digit. */
    private static String categoryName(String category) {
        String name = cleanName(category);

        return shopKey(name).isEmpty() ? OTHER_CATEGORY : name;
    }

    // ---- reading

    private static List<Item> read(List<Row> rows, LocalDate from, LocalDate today) {
        List<Item> items = new ArrayList<>();

        if (rows == null) {
            return items;
        }

        for (Row row : rows) {
            if (row == null || row.date() == null || row.date().isBefore(from) || row.date().isAfter(today)) {
                continue;
            }

            String key = shopKey(row.merchant());
            boolean shop = !isUnknownShop(key);
            BigDecimal amount = row.amount() == null || row.amount().signum() <= 0 ? BigDecimal.ZERO : row.amount();

            items.add(new Item(
                    row.ticket(),
                    shop ? key : "",
                    shop ? cleanName(row.merchant()) : "",
                    row.category(),
                    money(amount),
                    row.date()
            ));
        }

        return items;
    }

    /** The rows of the shops, by key: the rows without a shop are left out. */
    private static Map<String, List<Item>> byShop(List<Item> items) {
        Map<String, List<Item>> shops = new TreeMap<>();

        for (Item item : items) {
            if (!item.key().isEmpty()) {
                shops.computeIfAbsent(item.key(), k -> new ArrayList<>()).add(item);
            }
        }

        return shops;
    }

    /** True when tickets were saved on at least 3 different days before the last 90 days. */
    private static boolean hasBaseline(List<Item> history, LocalDate today) {
        LocalDate recentStart = today.minusDays(RECENT_DAYS - 1L);
        Set<LocalDate> days = new HashSet<>();

        for (Item item : history) {
            if (item.date().isBefore(recentStart)) {
                days.add(item.date());
            }
        }

        return days.size() >= BASELINE_MIN_DAYS;
    }

    // ---- one shop

    /** The figures of a shop from its rows (the history); null when it has no row in the period. */
    private static ShopResponse line(String key, List<Item> history, LocalDate from, LocalDate today, BigDecimal totalSpent, boolean baseline) {
        LocalDate recentStart = today.minusDays(RECENT_DAYS - 1L);
        LocalDate previousStart = recentStart.minusDays(RECENT_DAYS);

        BigDecimal spent = BigDecimal.ZERO;
        int count = 0;
        TreeSet<LocalDate> visitDays = new TreeSet<>();
        List<String> names = new ArrayList<>();
        BigDecimal recent = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        int recentRows = 0;
        Set<LocalDate> previousDays = new HashSet<>();
        boolean before = false;

        for (Item item : history) {
            if (!item.date().isBefore(from)) {
                spent = spent.add(item.amount());
                count++;
                visitDays.add(item.date());
                names.add(item.name());
            }

            if (!item.date().isBefore(recentStart)) {
                recent = recent.add(item.amount());
                recentRows++;
            } else {
                before = true;

                if (!item.date().isBefore(previousStart)) {
                    previous = previous.add(item.amount());
                    previousDays.add(item.date());
                }
            }
        }

        if (count == 0) {
            return null;
        }

        LocalDate first = visitDays.first();
        LocalDate last = visitDays.last();
        BigDecimal gap = visitDays.size() >= 2
                ? divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(first, last)), visitDays.size() - 1, 1)
                : null;

        BigDecimal change = previous.signum() > 0
                ? recent.subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP)
                : null;

        String status = STATUS_STEADY;

        if (baseline && !before && recentRows > 0) {
            status = STATUS_NEW;
        } else if (previousDays.size() >= FADING_MIN_VISIT_DAYS && recentRows == 0) {
            status = STATUS_FADING;
        } else if (previous.signum() > 0
                && recent.multiply(BigDecimal.valueOf(4)).compareTo(previous.multiply(BigDecimal.valueOf(5))) >= 0
                && recent.subtract(previous).compareTo(MIN_CHANGE) >= 0) {
            status = STATUS_UP;
        } else if (previous.signum() > 0
                && recent.multiply(BigDecimal.valueOf(4)).compareTo(previous.multiply(BigDecimal.valueOf(3))) <= 0
                && previous.subtract(recent).compareTo(MIN_CHANGE) >= 0) {
            status = STATUS_DOWN;
        }

        return new ShopResponse(
                key,
                mostFrequent(names),
                money(spent),
                totalSpent.signum() > 0 ? percent(spent, totalSpent) : null,
                count,
                visitDays.size(),
                divide(spent, count, 2),
                first.toString(),
                last.toString(),
                (int) ChronoUnit.DAYS.between(last, today),
                gap,
                money(recent),
                money(previous),
                change,
                status
        );
    }

    // ---- numbers

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

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal value, int by, int scale) {
        return value.divide(BigDecimal.valueOf(by), scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
