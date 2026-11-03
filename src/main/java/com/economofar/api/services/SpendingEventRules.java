package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What an event of the person must look like to be kept, without any dependency on Spring.
 *
 * The name is written without invisible characters or extra spaces, 60 characters at most (a character
 * is a code point). Two names are the same when they have the same key: without capitals, accents or
 * extra spaces. The icon is one of the icons the app offers ("calendar" when missing). The budget is
 * missing or above zero to the cent and not above 10 million. The dates are "yyyy-MM-dd" or missing,
 * between the years 2000 and 2100; an end date needs a start date, is not before it, and the event lasts
 * 3660 days at most.
 */
public final class SpendingEventRules {

    /** What an event looks like once it has been checked. */
    public record Checked(String name, String nameKey, String icon, BigDecimal budget, LocalDate startDate, LocalDate endDate) {
    }

    static final int MAX_NAME_LENGTH = 60;
    static final BigDecimal MAX_BUDGET = new BigDecimal("10000000");
    static final BigDecimal HALF_CENT = new BigDecimal("0.005");
    static final int MAX_BUDGET_DIGITS = 30;
    static final String DEFAULT_ICON = "calendar";
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;
    static final int MAX_SPAN_DAYS = 3660;

    public static final List<String> ICONS = List.of(
            "calendar",
            "airplane",
            "house.fill",
            "gift.fill",
            "heart.fill",
            "car.fill",
            "graduationcap.fill",
            "cart.fill",
            "star.fill",
            "briefcase.fill",
            "wrench.and.screwdriver.fill",
            "fork.knife",
            "camera.fill",
            "sportscourt.fill"
    );

    private static final Pattern MARKS = Pattern.compile("[\\p{M}\\p{Cf}]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\p{Cc}&&[^\\s]]");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");

    private SpendingEventRules() {
    }

    /** A name without invisible characters, with single spaces and nothing around it. */
    public static String cleanName(String name) {
        if (name == null) {
            return "";
        }

        return SPACES.matcher(INVISIBLE.matcher(name).replaceAll("")).replaceAll(" ").trim();
    }

    /** The name without capitals, accents or extra spaces: what two spellings of a name have in common. */
    public static String nameKey(String name) {
        String text = cleanName(name).toLowerCase(Locale.ROOT);
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = MARKS.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ").trim();

        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /**
     * Checks the fields of a request and gives the event they stand for.
     *
     * @throws IllegalArgumentException with the reason when a field is not acceptable
     */
    public static Checked check(String name, String icon, BigDecimal budget, String startDate, String endDate) {
        String cleaned = cleanName(name);

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("A name is required");
        }

        if (cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("The name is too long: " + MAX_NAME_LENGTH + " characters at most");
        }

        String key = nameKey(cleaned);

        if (key.isEmpty()) {
            throw new IllegalArgumentException("A name is required");
        }

        String chosenIcon = icon == null || icon.isBlank() ? DEFAULT_ICON : icon.trim();

        if (!ICONS.contains(chosenIcon)) {
            throw new IllegalArgumentException("This icon is not offered");
        }

        BigDecimal chosenBudget = null;

        if (budget != null) {
            // Comparing is cheap whatever the exponent, rounding is not: a number like 1e-60000000 is refused
            // before it is rounded. Under half a cent it would round to zero.
            if (budget.compareTo(MAX_BUDGET) > 0) {
                throw new IllegalArgumentException("The budget is too high: 10 million at most");
            }

            if (budget.compareTo(HALF_CENT) < 0) {
                throw new IllegalArgumentException("The budget must be more than zero");
            }

            if (budget.precision() > MAX_BUDGET_DIGITS) {
                throw new IllegalArgumentException("The budget has too many digits: " + MAX_BUDGET_DIGITS + " at most");
            }

            chosenBudget = budget.setScale(2, RoundingMode.HALF_UP);
        }

        LocalDate start = parseDate(startDate, "start");
        LocalDate end = parseDate(endDate, "end");

        if (end != null && start == null) {
            throw new IllegalArgumentException("An end date needs a start date");
        }

        if (start != null && end != null) {
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("The end date is before the start date");
            }

            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SPAN_DAYS) {
                throw new IllegalArgumentException("The event is too long: " + MAX_SPAN_DAYS + " days at most");
            }
        }

        return new Checked(cleaned, key, chosenIcon, chosenBudget, start, end);
    }

    /** A date written "yyyy-MM-dd", null when there is nothing written. */
    static LocalDate parseDate(String text, String which) {
        if (text == null || text.isBlank()) {
            return null;
        }

        LocalDate date;

        try {
            date = LocalDate.parse(text.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("The " + which + " date is not a date: use yyyy-MM-dd");
        }

        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw new IllegalArgumentException("The " + which + " date must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }

        return date;
    }
}
