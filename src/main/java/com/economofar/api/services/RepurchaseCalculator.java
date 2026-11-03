package com.economofar.api.services;

import com.economofar.api.dtos.RepurchaseArticleResponse;
import com.economofar.api.dtos.RepurchaseDetailResponse;
import com.economofar.api.dtos.RepurchasePurchaseResponse;
import com.economofar.api.dtos.RepurchaseResponse;
import com.economofar.api.dtos.RepurchaseShopResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds the articles that are bought at a regular rhythm and tells when to buy them again.
 *
 * An article is the name written on a ticket line, compared without capitals, accents, invisible
 * characters, punctuation or extra spaces (the key, 120 characters at most). A line named "Article" (the
 * name the app gives to a line without name) or with no letter or digit is ignored, as is a line with no
 * date, in the future or before the period. The quantity of a line is at least 1. The amount of a line is
 * its total price, as everywhere else in the app; when the total price is missing or not above zero it is
 * the unit price times the quantity; both rounded to the cent. A line with no price above zero is still a
 * purchase, with an amount of 0.00.
 *
 * The period is the current month and the months before it: 12 by default, 3 at least and 24 at most. An
 * article is followed when it was bought on 3 different days or more in the period. The gaps are the
 * numbers of days between two purchase days that follow each other, the cycle is the middle one (the
 * average of the two middle ones, rounded up, when there is an even number of gaps). The rhythm is regular
 * when at least two thirds of the gaps are within half a cycle of the cycle; only the regular articles are
 * listed.
 *
 * With daysSince the days from the last purchase day to today: the status is LAPSED when daysSince is more
 * than 3 cycles, else DUE from one cycle, else SOON from three quarters of a cycle, else OK. The list shows
 * the DUE articles first, then SOON, OK and LAPSED, and in each the one that is the furthest through its
 * cycle (daysSince / cycle) first.
 *
 * The usual shop is the named shop where the article was bought on the most different days (the smallest
 * key when several are tied). A shop is named when the ticket has a shop name that is not a placeholder
 * ("Enseigne inconnue", "Commer\u00e7ant inconnu", "Magasin inconnu", "Analyse en cours", "Inconnu") and has
 * a letter or a digit.
 *
 * The answer never depends on the order of the lines. This class has no dependency on Spring so that it
 * can be tested alone.
 */
public final class RepurchaseCalculator {

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

    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 24;
    public static final int MAX_KEY_LENGTH = 120;
    public static final int MIN_PURCHASE_DAYS = 3;
    public static final int LAPSED_CYCLES = 3;
    public static final int MAX_ARTICLES = 100;
    public static final int MAX_INTERVALS = 30;
    public static final int MAX_PURCHASES = 20;
    public static final int MAX_SHOPS = 5;

    static final String STATUS_DUE = "DUE";
    static final String STATUS_SOON = "SOON";
    static final String STATUS_OK = "OK";
    static final String STATUS_LAPSED = "LAPSED";

    static final String PLACEHOLDER_ARTICLE = "article";

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

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private static final Pattern MARKS = Pattern.compile("[\\p{M}\\p{Cf}]");
    private static final Pattern APOSTROPHES = Pattern.compile("['\u2018\u2019\u02bc]");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");

    private RepurchaseCalculator() {
    }

    /** A line once read: shopKey is empty for a ticket that has no named shop. */
    private record Item(String ticket, String key, String name, String shopKey, String shopName, LocalDate date, int quantity, BigDecimal amount) {
    }

    /**
     * What is known about one article: followed tells that it was bought on enough days, article is null
     * when the rhythm is not regular.
     */
    private record Habit(boolean followed, RepurchaseArticleResponse article, List<Integer> intervals, List<Item> items,
                         List<RepurchaseShopResponse> shops) {
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
     * The way two names are compared: lowercased, without accents, invisible characters or apostrophes,
     * every run of characters that are not letters or digits turned into one space, cut to 120 characters.
     * An empty text is returned for a name that is missing. The key of a key is the key itself.
     */
    public static String articleKey(String name) {
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

    /** True for a key that is empty or only a placeholder such as "enseigne inconnue" (spaces do not count). */
    private static boolean isUnknownShop(String key) {
        StringBuilder letters = new StringBuilder();
        key.codePoints().filter(RepurchaseCalculator::isLetterOrNumber).forEach(letters::appendCodePoint);

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

    public static RepurchaseResponse overview(List<Line> lines, LocalDate today, int requestedMonths) {
        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        Map<String, List<Item>> articles = byArticle(read(lines, from, today));

        int followed = 0;
        int due = 0;
        int soon = 0;
        int ok = 0;
        int lapsed = 0;
        BigDecimal monthly = BigDecimal.ZERO;
        List<RepurchaseArticleResponse> regular = new ArrayList<>();

        for (Map.Entry<String, List<Item>> entry : articles.entrySet()) {
            Habit habit = habit(entry.getKey(), entry.getValue(), today);

            if (habit.followed()) {
                followed++;
            }

            RepurchaseArticleResponse article = habit.article();

            if (article == null) {
                continue;
            }

            regular.add(article);

            switch (article.status()) {
                case STATUS_DUE -> due++;
                case STATUS_SOON -> soon++;
                case STATUS_OK -> ok++;
                default -> lapsed++;
            }

            if (!STATUS_LAPSED.equals(article.status())) {
                monthly = monthly.add(article.monthlyEstimate());
            }
        }

        regular.sort(Comparator
                .comparingInt((RepurchaseArticleResponse article) -> rank(article.status()))
                .thenComparing((a, b) -> Long.compare((long) b.daysSinceLast() * a.cycleDays(), (long) a.daysSinceLast() * b.cycleDays()))
                .thenComparing(RepurchaseArticleResponse::key));

        List<RepurchaseArticleResponse> listed = regular.size() > MAX_ARTICLES ? new ArrayList<>(regular.subList(0, MAX_ARTICLES)) : regular;

        return new RepurchaseResponse(
                months,
                from.toString(),
                today.toString(),
                followed,
                regular.size(),
                followed - regular.size(),
                due,
                soon,
                ok,
                lapsed,
                money(monthly),
                regular.size() - listed.size(),
                listed
        );
    }

    /** One article with a regular rhythm, or null when the key is not one. */
    public static RepurchaseDetailResponse detail(List<Line> lines, String key, LocalDate today, int requestedMonths) {
        String wanted = articleKey(key);

        if (wanted.isEmpty() || PLACEHOLDER_ARTICLE.equals(wanted)) {
            return null;
        }

        int months = clampMonths(requestedMonths);
        LocalDate from = periodStart(today, months);
        List<Item> own = byArticle(read(lines, from, today)).get(wanted);

        if (own == null) {
            return null;
        }

        Habit habit = habit(wanted, own, today);

        if (habit.article() == null) {
            return null;
        }

        List<Integer> intervals = habit.intervals();
        int shownIntervals = Math.min(MAX_INTERVALS, intervals.size());
        List<Integer> recentIntervals = new ArrayList<>(intervals.subList(intervals.size() - shownIntervals, intervals.size()));

        List<Item> latest = new ArrayList<>(own);
        latest.sort(Comparator
                .comparing(Item::date, Comparator.reverseOrder())
                .thenComparing(Item::amount, Comparator.reverseOrder())
                .thenComparing(item -> item.ticket() == null ? "" : item.ticket())
                .thenComparing(Item::quantity, Comparator.reverseOrder())
                .thenComparing(Item::shopName));

        List<RepurchasePurchaseResponse> purchases = new ArrayList<>();

        for (Item item : latest.subList(0, Math.min(MAX_PURCHASES, latest.size()))) {
            purchases.add(new RepurchasePurchaseResponse(
                    item.ticket() == null ? "" : item.ticket(),
                    item.date().toString(),
                    item.quantity(),
                    item.amount(),
                    item.shopName()
            ));
        }

        return new RepurchaseDetailResponse(
                months,
                from.toString(),
                today.toString(),
                habit.article(),
                recentIntervals,
                intervals.size() - recentIntervals.size(),
                purchases,
                latest.size() - purchases.size(),
                habit.shops()
        );
    }

    // ---- reading

    private static List<Item> read(List<Line> lines, LocalDate from, LocalDate today) {
        List<Item> items = new ArrayList<>();

        if (lines == null) {
            return items;
        }

        for (Line line : lines) {
            if (line == null || line.date() == null || line.date().isBefore(from) || line.date().isAfter(today)) {
                continue;
            }

            String key = articleKey(line.name());

            if (key.isEmpty() || PLACEHOLDER_ARTICLE.equals(key)) {
                continue;
            }

            int quantity = line.quantity() == null || line.quantity() < 1 ? 1 : line.quantity();
            BigDecimal amount = BigDecimal.ZERO;

            if (line.totalPrice() != null && line.totalPrice().signum() > 0) {
                amount = line.totalPrice();
            } else if (line.unitPrice() != null && line.unitPrice().signum() > 0) {
                amount = line.unitPrice().multiply(BigDecimal.valueOf(quantity));
            }

            String shopKey = articleKey(line.merchant());
            boolean named = !isUnknownShop(shopKey);

            items.add(new Item(
                    line.ticket(),
                    key,
                    cleanName(line.name()),
                    named ? shopKey : "",
                    named ? cleanName(line.merchant()) : "",
                    line.date(),
                    quantity,
                    money(amount)
            ));
        }

        return items;
    }

    private static Map<String, List<Item>> byArticle(List<Item> items) {
        Map<String, List<Item>> articles = new TreeMap<>();

        for (Item item : items) {
            articles.computeIfAbsent(item.key(), k -> new ArrayList<>()).add(item);
        }

        return articles;
    }

    // ---- one article

    private static int rank(String status) {
        return switch (status) {
            case STATUS_DUE -> 0;
            case STATUS_SOON -> 1;
            case STATUS_OK -> 2;
            default -> 3;
        };
    }

    private static Habit habit(String key, List<Item> items, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>();

        for (Item item : items) {
            days.add(item.date());
        }

        if (days.size() < MIN_PURCHASE_DAYS) {
            return new Habit(false, null, List.of(), items, List.of());
        }

        List<Integer> intervals = new ArrayList<>();
        LocalDate previousDay = null;

        for (LocalDate day : days) {
            if (previousDay != null) {
                intervals.add((int) ChronoUnit.DAYS.between(previousDay, day));
            }

            previousDay = day;
        }

        List<Integer> sorted = new ArrayList<>(intervals);
        Collections.sort(sorted);

        int count = sorted.size();
        int cycle = count % 2 == 1
                ? sorted.get(count / 2)
                : (sorted.get(count / 2 - 1) + sorted.get(count / 2) + 1) / 2;
        int within = 0;

        for (int interval : intervals) {
            if (2 * Math.abs(interval - cycle) <= cycle) {
                within++;
            }
        }

        if (3 * within < 2 * count) {
            return new Habit(true, null, intervals, items, List.of());
        }

        BigDecimal spent = BigDecimal.ZERO;
        long quantity = 0;
        List<String> names = new ArrayList<>();
        Map<String, TreeSet<LocalDate>> shopDays = new TreeMap<>();
        Map<String, List<String>> shopNames = new HashMap<>();

        for (Item item : items) {
            spent = spent.add(item.amount());
            quantity += item.quantity();
            names.add(item.name());

            if (!item.shopKey().isEmpty()) {
                shopDays.computeIfAbsent(item.shopKey(), k -> new TreeSet<>()).add(item.date());
                shopNames.computeIfAbsent(item.shopKey(), k -> new ArrayList<>()).add(item.shopName());
            }
        }

        List<String> shopOrder = new ArrayList<>(shopDays.keySet());
        shopOrder.sort(Comparator
                .comparing((String shop) -> shopDays.get(shop).size(), Comparator.reverseOrder())
                .thenComparing(shop -> shop));

        List<RepurchaseShopResponse> shops = new ArrayList<>();

        for (String shop : shopOrder.subList(0, Math.min(MAX_SHOPS, shopOrder.size()))) {
            shops.add(new RepurchaseShopResponse(mostFrequent(shopNames.get(shop)), shopDays.get(shop).size()));
        }

        LocalDate first = days.first();
        LocalDate last = days.last();
        int daysSince = (int) ChronoUnit.DAYS.between(last, today);
        int purchaseDays = days.size();

        String status;

        if (daysSince > (long) LAPSED_CYCLES * cycle) {
            status = STATUS_LAPSED;
        } else if (daysSince >= cycle) {
            status = STATUS_DUE;
        } else if (4L * daysSince >= 3L * cycle) {
            status = STATUS_SOON;
        } else {
            status = STATUS_OK;
        }

        RepurchaseArticleResponse article = new RepurchaseArticleResponse(
                key,
                mostFrequent(names),
                status,
                cycle,
                sorted.get(0),
                sorted.get(count - 1),
                purchaseDays,
                quantity,
                divide(BigDecimal.valueOf(quantity), purchaseDays, 1),
                money(spent),
                divide(spent, purchaseDays, 2),
                spent.multiply(THIRTY).divide(BigDecimal.valueOf((long) purchaseDays * cycle), 2, RoundingMode.HALF_UP),
                first.toString(),
                last.toString(),
                last.plusDays(cycle).toString(),
                daysSince,
                cycle - daysSince,
                shops.isEmpty() ? null : shops.get(0).name()
        );

        return new Habit(true, article, intervals, items, shops);
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

    private static BigDecimal divide(BigDecimal value, int by, int scale) {
        return value.divide(BigDecimal.valueOf(by), scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
