package com.economofar.api.services;

import com.economofar.api.dtos.CategoryMerchantResponse;
import com.economofar.api.dtos.CategoryProfileResponse;
import com.economofar.api.dtos.CategoryProfilesResponse;
import com.economofar.api.dtos.CategoryTicketResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a profile for each category from the saved tickets dated from {@code months} months back
 * from today (1 to 24, 12 by default) up to today. A ticket dated after today, without date or
 * without id is ignored.
 *
 * A category is the same whatever the case, the accents and the punctuation, and it is named as it
 * is written most often (the first in alphabetical order when several are written as often). A
 * ticket without category is in "Autre". Merchants are grouped the same way inside a category; an
 * empty merchant and the placeholders of the scan ("Commercant inconnu", "Magasin inconnu", "Analyse en
 * cours", "Enseigne inconnue", "Inconnu") are one entry named "Enseigne inconnue".
 *
 * Categories come with most spending first, as it is shown (then more tickets, then the first in
 * alphabetical order of the category without case, accents and punctuation), 100 at most with an
 * exact count. Amounts count only the values above zero. The answer never depends on the order of
 * the tickets.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class CategoryProfilesCalculator {

    public record Entry(UUID id, String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int DEFAULT_MONTHS = 12;
    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;

    static final int MAX_CATEGORIES = 100;
    static final int MAX_MERCHANTS = 5;
    static final int MAX_RECENT = 10;

    static final String OTHER_CATEGORY = "Autre";
    static final String UNKNOWN_MERCHANT = "Enseigne inconnue";

    private static final String UNKNOWN_KEY = "enseigneinconnue";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            UNKNOWN_KEY,
            "inconnu"
    );

    private CategoryProfilesCalculator() {
    }

    public static int clampMonths(Integer requested) {
        if (requested == null) {
            return DEFAULT_MONTHS;
        }

        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    /** The first day of the tickets that are looked at. */
    public static LocalDate windowStart(LocalDate today, Integer requestedMonths) {
        return today.minusMonths(clampMonths(requestedMonths));
    }

    public static CategoryProfilesResponse analyse(List<Entry> entries, LocalDate today, Integer requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);
        LocalDate from = today.minusMonths(months);

        int ticketCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, Category> categories = new HashMap<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.id() == null || entry.date() == null) {
                    continue;
                }

                if (entry.date().isBefore(from) || entry.date().isAfter(today)) {
                    continue;
                }

                BigDecimal amount = entry.amount() != null && entry.amount().signum() > 0 ? entry.amount() : BigDecimal.ZERO;
                String categoryText = categoryLabel(entry.category());

                ticketCount++;
                totalAmount = totalAmount.add(amount);

                categories.computeIfAbsent(key(categoryText), Category::new)
                        .add(categoryText, tidy(entry.merchant()), amount, entry.id(), entry.date());
            }
        }

        List<Category> ordered = new ArrayList<>(categories.values());

        ordered.sort(
                Comparator.comparing((Category c) -> money(c.total), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Category c) -> c.tickets.size(), Comparator.<Integer>reverseOrder())
                        .thenComparing((Category c) -> c.key)
        );

        List<CategoryProfileResponse> result = new ArrayList<>();

        for (int i = 0; i < ordered.size() && i < MAX_CATEGORIES; i++) {
            result.add(ordered.get(i).toResponse(today, totalAmount));
        }

        return new CategoryProfilesResponse(
                months,
                from.toString(),
                today.toString(),
                ticketCount,
                money(totalAmount),
                ordered.size(),
                result
        );
    }

    private record Ticket(UUID id, LocalDate date, BigDecimal amount, String merchantKey) {
    }

    private static final class Shop {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        int tickets = 0;
        BigDecimal amount = BigDecimal.ZERO;

        Shop(String key) {
            this.key = key;
        }
    }

    private static final class Category {
        final String key;
        final Map<String, Integer> spellings = new HashMap<>();
        final Map<String, Shop> shops = new HashMap<>();
        final List<Ticket> tickets = new ArrayList<>();
        final Set<LocalDate> days = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        int priced = 0;
        int unknown = 0;

        Category(String key) {
            this.key = key;
        }

        void add(String text, String merchantText, BigDecimal amount, UUID id, LocalDate date) {
            String merchantKey = key(merchantText);
            String merchantName = merchantText;

            if (merchantKey.isEmpty() || UNKNOWN_MERCHANT_KEYS.contains(merchantKey)) {
                merchantKey = UNKNOWN_KEY;
                merchantName = UNKNOWN_MERCHANT;
                unknown++;
            }

            Shop shop = shops.computeIfAbsent(merchantKey, Shop::new);

            shop.spellings.merge(merchantName, 1, Integer::sum);
            shop.tickets++;
            shop.amount = shop.amount.add(amount);

            spellings.merge(text, 1, Integer::sum);
            tickets.add(new Ticket(id, date, amount, merchantKey));
            days.add(date);
            total = total.add(amount);

            if (amount.signum() > 0) {
                priced++;
            }
        }

        CategoryProfileResponse toResponse(LocalDate today, BigDecimal allSpending) {
            tickets.sort(
                    Comparator.comparing((Ticket t) -> t.date(), Comparator.<LocalDate>reverseOrder())
                            .thenComparing((Ticket t) -> t.amount(), Comparator.<BigDecimal>reverseOrder())
                            .thenComparing((Ticket t) -> t.id().toString())
            );

            LocalDate last = tickets.get(0).date();
            LocalDate first = last;

            for (Ticket ticket : tickets) {
                if (ticket.date().isBefore(first)) {
                    first = ticket.date();
                }
            }

            List<CategoryTicketResponse> recent = new ArrayList<>();

            for (int i = 0; i < tickets.size() && i < MAX_RECENT; i++) {
                Ticket ticket = tickets.get(i);

                recent.add(new CategoryTicketResponse(
                        ticket.id(),
                        mostUsed(shops.get(ticket.merchantKey()).spellings),
                        ticket.date().toString(),
                        money(ticket.amount())
                ));
            }

            BigDecimal average = priced == 0
                    ? null
                    : total.divide(BigDecimal.valueOf(priced), 2, RoundingMode.HALF_UP);

            BigDecimal share = allSpending.signum() == 0
                    ? null
                    : total.multiply(BigDecimal.valueOf(100)).divide(allSpending, 1, RoundingMode.HALF_UP);

            Integer daysBetweenPurchases = null;

            if (days.size() >= 2) {
                long span = ChronoUnit.DAYS.between(first, last);
                long gaps = days.size() - 1L;

                daysBetweenPurchases = (int) ((2 * span + gaps) / (2 * gaps));
            }

            return new CategoryProfileResponse(
                    key,
                    mostUsed(spellings),
                    tickets.size(),
                    days.size(),
                    money(total),
                    average,
                    share,
                    first.toString(),
                    last.toString(),
                    (int) ChronoUnit.DAYS.between(last, today),
                    daysBetweenPurchases,
                    shops.containsKey(UNKNOWN_KEY) ? shops.size() - 1 : shops.size(),
                    unknown,
                    topMerchants(),
                    recent
            );
        }

        private List<CategoryMerchantResponse> topMerchants() {
            List<Shop> spent = new ArrayList<>();

            for (Shop shop : shops.values()) {
                if (shop.amount.signum() > 0) {
                    spent.add(shop);
                }
            }

            spent.sort(
                    Comparator.comparing((Shop s) -> money(s.amount), Comparator.<BigDecimal>reverseOrder())
                            .thenComparing((Shop s) -> s.tickets, Comparator.<Integer>reverseOrder())
                            .thenComparing((Shop s) -> s.key)
            );

            List<CategoryMerchantResponse> result = new ArrayList<>();

            for (int i = 0; i < spent.size() && i < MAX_MERCHANTS; i++) {
                Shop shop = spent.get(i);

                result.add(new CategoryMerchantResponse(mostUsed(shop.spellings), shop.tickets, money(shop.amount)));
            }

            return result;
        }
    }

    /** The category as written, or "Autre" when there is none. */
    private static String categoryLabel(String category) {
        String text = tidy(category);

        return key(text).isEmpty() ? OTHER_CATEGORY : text;
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
