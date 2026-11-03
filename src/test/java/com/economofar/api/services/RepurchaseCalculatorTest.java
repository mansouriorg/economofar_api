package com.economofar.api.services;

import com.economofar.api.dtos.RepurchaseArticleResponse;
import com.economofar.api.dtos.RepurchaseDetailResponse;
import com.economofar.api.dtos.RepurchaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RepurchaseCalculatorTest {

    private static final LocalDate LAST = LocalDate.of(2026, 10, 10);
    private static final String MILK = "Lait demi-\u00e9cr\u00e9m\u00e9 1L";

    private static RepurchaseCalculator.Line line(String ticket, String name, String shop, LocalDate date, Integer quantity, String unit, String total) {
        return new RepurchaseCalculator.Line(
                ticket, name, shop, date, quantity,
                unit == null ? null : new BigDecimal(unit),
                total == null ? null : new BigDecimal(total)
        );
    }

    private static List<RepurchaseCalculator.Line> weekly(String name, String shop, int weeks) {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < weeks; i++) {
            lines.add(line("t" + i, name, shop, LAST.minusDays(7L * i), 6, "1.00", "6.00"));
        }

        return lines;
    }

    private static List<RepurchaseCalculator.Line> onDays(String name, String shop, int... daysBeforeLast) {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < daysBeforeLast.length; i++) {
            lines.add(line("d" + i, name, shop, LAST.minusDays(daysBeforeLast[i]), 1, "2.00", "2.00"));
        }

        return lines;
    }

    private static String statusAfter(int daysSinceLast) {
        RepurchaseResponse response = RepurchaseCalculator.overview(weekly(MILK, "Lidl", 10), LAST.plusDays(daysSinceLast), 12);

        assertEquals(1, response.articles().size());

        return response.articles().get(0).status();
    }

    private static List<RepurchaseCalculator.Line> weeklyEndingEarlier(String name, int days) {
        return weekly(name, "Lidl", 10).stream()
                .map(l -> line(l.ticket() + name, l.name(), l.merchant(), l.date().minusDays(days), l.quantity(), "1.00", "6.00"))
                .toList();
    }

    private static String statusOf(List<RepurchaseCalculator.Line> lines, int daysAfterLast) {
        RepurchaseResponse response = RepurchaseCalculator.overview(lines, LAST.plusDays(daysAfterLast), 12);

        assertEquals(1, response.articles().size());

        return response.articles().get(0).status();
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("two names are the same article whatever the capitals, the accents and the punctuation")
    void articleKeyIgnoresCapitalsAccentsAndPunctuation() {
        assertEquals("lait demi ecreme 1l", RepurchaseCalculator.articleKey("Lait demi-\u00e9cr\u00e9m\u00e9 1L"));
        assertEquals("lait demi ecreme 1l", RepurchaseCalculator.articleKey("  LAIT   DEMI ECREME, 1l "));
        assertEquals("jus dorange 1l", RepurchaseCalculator.articleKey("Jus d'orange 1L"));
        assertEquals("", RepurchaseCalculator.articleKey(null));
    }

    @Test
    @DisplayName("the number of months is kept between 3 and 24")
    void monthsAreClamped() {
        assertEquals(3, RepurchaseCalculator.clampMonths(0));
        assertEquals(3, RepurchaseCalculator.clampMonths(3));
        assertEquals(12, RepurchaseCalculator.clampMonths(12));
        assertEquals(24, RepurchaseCalculator.clampMonths(500));
    }

    @Test
    @DisplayName("the period starts on the first day of the month, months minus one months back")
    void periodStartsOnTheFirstDayOfAMonth() {
        assertEquals(LocalDate.of(2026, 8, 1), RepurchaseCalculator.periodStart(LocalDate.of(2026, 10, 23), 3));
        assertEquals(LocalDate.of(2025, 11, 1), RepurchaseCalculator.periodStart(LocalDate.of(2026, 10, 23), 12));
    }

    @Test
    @DisplayName("milk bought every week is a regular article with a cycle of seven days")
    void weeklyMilkIsRegular() {
        RepurchaseResponse response = RepurchaseCalculator.overview(weekly(MILK, "Lidl", 10), LAST.plusDays(2), 12);

        assertEquals(1, response.articleCount());
        assertEquals(1, response.articles().size());
        assertEquals(0, response.irregularCount());

        RepurchaseArticleResponse milk = response.articles().get(0);

        assertEquals("lait demi ecreme 1l", milk.key());
        assertEquals(MILK, milk.name());
        assertEquals("OK", milk.status());
        assertEquals(7, milk.cycleDays());
        assertEquals(7, milk.minIntervalDays());
        assertEquals(7, milk.maxIntervalDays());
        assertEquals(10, milk.purchaseDays());
        assertEquals(60, milk.totalQuantity());
        assertEquals(0, new BigDecimal("60.00").compareTo(milk.spent()));
        assertEquals("2026-10-10", milk.lastDate());
        assertEquals("2026-10-17", milk.nextDate());
        assertEquals(2, milk.daysSinceLast());
        assertEquals(5, milk.daysUntilNext());
        assertEquals("Lidl", milk.usualShop());
    }

    @Test
    @DisplayName("the status goes from OK to SOON, DUE and LAPSED as the days pass")
    void statusFollowsTheCycle() {
        assertEquals("OK", statusAfter(0));
        assertEquals("OK", statusAfter(5));
        assertEquals("SOON", statusAfter(6));
        assertEquals("DUE", statusAfter(7));
        assertEquals("DUE", statusAfter(21));
        assertEquals("LAPSED", statusAfter(22));
    }

    @Test
    @DisplayName("the articles to buy again come first")
    void dueArticlesComeFirst() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>(weekly(MILK, "Lidl", 10));
        lines.addAll(weekly("Caf\u00e9 en grains", "Lidl", 10).stream()
                .map(l -> line(l.ticket(), l.name(), l.merchant(), l.date().minusDays(10), l.quantity(), "1.00", "6.00"))
                .toList());

        RepurchaseResponse response = RepurchaseCalculator.overview(lines, LAST.plusDays(2), 12);

        assertEquals(2, response.articles().size());
        assertEquals("DUE", response.articles().get(0).status());
        assertEquals("Caf\u00e9 en grains", response.articles().get(0).name());
        assertEquals("OK", response.articles().get(1).status());
        assertEquals(1, response.dueCount());
        assertEquals(1, response.okCount());
    }

    @Test
    @DisplayName("an article bought on only two days is not followed")
    void twoPurchaseDaysAreNotEnough() {
        RepurchaseResponse response = RepurchaseCalculator.overview(onDays("Sel fin", "Lidl", 0, 30), LAST, 12);

        assertEquals(0, response.articleCount());
        assertEquals(0, response.articles().size());
    }

    @Test
    @DisplayName("an article bought without a rhythm is counted as irregular and not listed")
    void irregularRhythmIsNotListed() {
        RepurchaseResponse response = RepurchaseCalculator.overview(
                onDays("Vis", "Leroy Merlin", 0, 2, 32, 35, 80, 81, 170), LAST, 12);

        assertEquals(0, response.articles().size());
        assertEquals(1, response.irregularCount());
    }

    @Test
    @DisplayName("several lines of the same article on the same day are one purchase day")
    void sameDayLinesAreOnePurchaseDay() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>(weekly(MILK, "Lidl", 6));
        lines.add(line("extra", MILK, "Lidl", LAST, 2, "1.00", "2.00"));

        RepurchaseArticleResponse milk = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertEquals(6, milk.purchaseDays());
        assertEquals(38, milk.totalQuantity());
        assertEquals(7, milk.cycleDays());
    }

    @Test
    @DisplayName("the usual shop is the one where the article was bought on the most days")
    void usualShopHasTheMostPurchaseDays() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            lines.add(line("a" + i, MILK, i < 4 ? "Lidl" : "Monoprix", LAST.minusDays(7L * i), 1, "1.00", "1.00"));
        }

        RepurchaseArticleResponse milk = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertEquals("Lidl", milk.usualShop());
    }

    @Test
    @DisplayName("a shop that is only a placeholder is never the usual shop")
    void unknownShopIsNeverTheUsualShop() {
        RepurchaseArticleResponse milk = RepurchaseCalculator.overview(weekly(MILK, "Enseigne inconnue", 6), LAST, 12).articles().get(0);

        assertNull(milk.usualShop());
    }

    @Test
    @DisplayName("purchases before the period are ignored")
    void oldPurchasesAreIgnored() {
        RepurchaseResponse response = RepurchaseCalculator.overview(weekly(MILK, "Lidl", 10), LAST.plusDays(300), 3);

        assertEquals(0, response.articles().size());
        assertEquals(0, response.followedCount());
    }

    @Test
    @DisplayName("the price of a line comes from its total, then from its unit price times its quantity")
    void amountOfALine() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        lines.add(line("a", "Pain", "Fournil", LAST, 3, "1.00", "3.30"));
        lines.add(line("b", "Pain", "Fournil", LAST.minusDays(4), 2, "1.50", null));
        lines.add(line("c", "Pain", "Fournil", LAST.minusDays(8), 1, null, null));
        lines.add(line("d", "Pain", "Fournil", LAST.minusDays(12), 1, "1.00", "1.00"));

        RepurchaseArticleResponse bread = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertEquals(4, bread.purchaseDays());
        assertEquals(7, bread.totalQuantity());
        assertEquals(0, new BigDecimal("7.30").compareTo(bread.spent()));
    }

    @Test
    @DisplayName("the answer does not depend on the order of the lines")
    void orderOfTheLinesDoesNotMatter() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>(weekly(MILK, "Lidl", 10));
        lines.addAll(weekly("Bananes 1kg", "Monoprix", 8));

        List<RepurchaseCalculator.Line> shuffled = new ArrayList<>(lines);
        Collections.reverse(shuffled);
        Collections.rotate(shuffled, 5);

        assertEquals(
                RepurchaseCalculator.overview(lines, LAST.plusDays(3), 12),
                RepurchaseCalculator.overview(shuffled, LAST.plusDays(3), 12)
        );
    }

    @Test
    @DisplayName("the detail gives the delays, the latest purchases and the shops of an article")
    void detailOfAnArticle() {
        RepurchaseDetailResponse detail = RepurchaseCalculator.detail(weekly(MILK, "Lidl", 10), "LAIT DEMI-ECREME 1L", LAST.plusDays(2), 12);

        assertNotNull(detail);
        assertEquals("lait demi ecreme 1l", detail.article().key());
        assertEquals(9, detail.intervals().size());
        assertTrue(detail.intervals().stream().allMatch(days -> days == 7));
        assertEquals(10, detail.purchases().size());
        assertEquals("2026-10-10", detail.purchases().get(0).date());
        assertEquals(1, detail.shops().size());
        assertEquals("Lidl", detail.shops().get(0).name());
        assertEquals(10, detail.shops().get(0).purchaseDays());
    }

    @Test
    @DisplayName("the detail of an article that is not followed is null")
    void detailOfAnUnknownArticleIsNull() {
        assertNull(RepurchaseCalculator.detail(weekly(MILK, "Lidl", 10), "Caviar", LAST, 12));
        assertNull(RepurchaseCalculator.detail(onDays("Sel fin", "Lidl", 0, 30), "Sel fin", LAST, 12));
    }

    @Test
    @DisplayName("an article bought on three days is already followed")
    void threePurchaseDaysAreEnough() {
        RepurchaseResponse response = RepurchaseCalculator.overview(onDays("Sel fin", "Lidl", 0, 30, 60), LAST, 12);

        assertEquals(1, response.articleCount());
        assertEquals(30, response.articles().get(0).cycleDays());
        assertEquals(3, response.articles().get(0).purchaseDays());
    }

    @Test
    @DisplayName("the cycle is the middle gap, or the middle of the two middle gaps when there is an even number")
    void cycleIsTheMiddleGap() {
        RepurchaseArticleResponse odd = RepurchaseCalculator.overview(onDays("Sel fin", "Lidl", 0, 10, 19, 26), LAST, 12).articles().get(0);

        assertEquals(9, odd.cycleDays());
        assertEquals(7, odd.minIntervalDays());
        assertEquals(10, odd.maxIntervalDays());

        RepurchaseArticleResponse even = RepurchaseCalculator.overview(onDays("Sel fin", "Lidl", 0, 9, 15), LAST, 12).articles().get(0);

        assertEquals(8, even.cycleDays());
        assertEquals(6, even.minIntervalDays());
        assertEquals(9, even.maxIntervalDays());
    }

    @Test
    @DisplayName("a rhythm is regular when two thirds of the gaps are close to the cycle")
    void twoThirdsOfTheGapsMustBeCloseToTheCycle() {
        RepurchaseResponse loose = RepurchaseCalculator.overview(onDays("Vis", "Leroy Merlin", 0, 40, 50, 60, 70, 72), LAST, 12);

        assertEquals(0, loose.articleCount());
        assertEquals(1, loose.irregularCount());

        RepurchaseResponse exact = RepurchaseCalculator.overview(onDays("Vis", "Leroy Merlin", 0, 40, 50, 60), LAST, 12);

        assertEquals(1, exact.articleCount());
        assertEquals(10, exact.articles().get(0).cycleDays());
    }

    @Test
    @DisplayName("a gap is close to the cycle when it differs from it by at most half of the cycle")
    void aGapIsCloseWithinHalfTheCycle() {
        assertEquals(1, RepurchaseCalculator.overview(onDays("Vis", "Lidl", 0, 15, 25, 35, 45, 85), LAST, 12).articleCount());
        assertEquals(0, RepurchaseCalculator.overview(onDays("Vis", "Lidl", 0, 16, 26, 36, 46, 86), LAST, 12).articleCount());
    }

    @Test
    @DisplayName("an article is SOON from three quarters of its cycle, DUE from the whole cycle and LAPSED after three cycles")
    void statusBoundariesWithAnEightDayCycle() {
        List<RepurchaseCalculator.Line> lines = onDays("Sel fin", "Lidl", 0, 8, 16, 24);

        assertEquals("OK", statusOf(lines, 5));
        assertEquals("SOON", statusOf(lines, 6));
        assertEquals("SOON", statusOf(lines, 7));
        assertEquals("DUE", statusOf(lines, 8));
        assertEquals("DUE", statusOf(lines, 24));
        assertEquals("LAPSED", statusOf(lines, 25));
    }

    @Test
    @DisplayName("the articles are listed DUE, SOON, OK and LAPSED, counted by status, and a lapsed one costs nothing")
    void articlesAreListedAndCountedByStatus() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        lines.addAll(weeklyEndingEarlier("Alpha", 0));
        lines.addAll(weeklyEndingEarlier("Bravo", 4));
        lines.addAll(weeklyEndingEarlier("Charlie", 5));
        lines.addAll(weeklyEndingEarlier("Delta", 30));
        Collections.reverse(lines);

        RepurchaseResponse response = RepurchaseCalculator.overview(lines, LAST.plusDays(2), 12);

        assertEquals(4, response.followedCount());
        assertEquals(4, response.articleCount());
        assertEquals(1, response.dueCount());
        assertEquals(1, response.soonCount());
        assertEquals(1, response.okCount());
        assertEquals(1, response.lapsedCount());
        assertEquals("Charlie", response.articles().get(0).name());
        assertEquals("Bravo", response.articles().get(1).name());
        assertEquals("Alpha", response.articles().get(2).name());
        assertEquals("Delta", response.articles().get(3).name());
        assertNumber("77.13", response.estimatedMonthly());
    }

    @Test
    @DisplayName("among articles with the same status, the most late comes first, then the name")
    void theMostLateComesFirst() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        lines.addAll(weeklyEndingEarlier("Zulu", 12));
        lines.addAll(weeklyEndingEarlier("Mike", 6));
        lines.addAll(weeklyEndingEarlier("Bravo", 6));

        RepurchaseResponse response = RepurchaseCalculator.overview(lines, LAST.plusDays(2), 12);

        assertEquals(3, response.dueCount());
        assertEquals("Zulu", response.articles().get(0).name());
        assertEquals("Bravo", response.articles().get(1).name());
        assertEquals("Mike", response.articles().get(2).name());
    }

    @Test
    @DisplayName("the detail shows the last 30 delays and the last 20 purchases and counts the others")
    void detailKeepsTheLatestDelaysAndPurchases() {
        RepurchaseDetailResponse detail = RepurchaseCalculator.detail(weekly(MILK, "Lidl", 32), MILK, LAST, 24);

        assertNotNull(detail);
        assertEquals(30, detail.intervals().size());
        assertEquals(1, detail.hiddenIntervalCount());
        assertEquals(20, detail.purchases().size());
        assertEquals(12, detail.hiddenPurchaseCount());
    }

    @Test
    @DisplayName("a very long name is cut to 120 characters")
    void longNamesAreCut() {
        String key = RepurchaseCalculator.articleKey("b" + "a".repeat(200));

        assertEquals(120, key.length());
        assertTrue(key.startsWith("baaa"));
        assertEquals(key, RepurchaseCalculator.articleKey(key));
        assertEquals(120, RepurchaseCalculator.articleKey("a".repeat(120)).length());
    }

    @Test
    @DisplayName("a shop with a one letter name is a shop, a missing or blank name is not")
    void shortShopNamesAreShops() {
        assertEquals("X", RepurchaseCalculator.overview(weekly(MILK, "X", 6), LAST, 12).articles().get(0).usualShop());
        assertNull(RepurchaseCalculator.overview(weekly(MILK, null, 6), LAST, 12).articles().get(0).usualShop());
        assertNull(RepurchaseCalculator.overview(weekly(MILK, "  ", 6), LAST, 12).articles().get(0).usualShop());
    }

    @Test
    @DisplayName("a missing, zero or negative quantity counts as one")
    void quantityIsAtLeastOne() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        lines.add(line("a", "Pain", "Fournil", LAST, null, "1.00", null));
        lines.add(line("b", "Pain", "Fournil", LAST.minusDays(4), 0, "1.00", null));
        lines.add(line("c", "Pain", "Fournil", LAST.minusDays(8), -2, "1.00", null));

        RepurchaseArticleResponse bread = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertEquals(3, bread.totalQuantity());
        assertNumber("3.00", bread.spent());
    }

    @Test
    @DisplayName("a total of zero is not a price: the unit price times the quantity is used")
    void zeroTotalFallsBackToTheUnitPrice() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            lines.add(line("t" + i, "Pain", "Fournil", LAST.minusDays(4L * i), 3, "2.00", "0.00"));
        }

        assertNumber("18.00", RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0).spent());
    }

    @Test
    @DisplayName("the averages per purchase day are rounded to one decimal for the quantity and two for the money")
    void averagesAreRounded() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        lines.add(line("a", "Pain", "Fournil", LAST, 4, "1.00", "4.00"));
        lines.add(line("b", "Pain", "Fournil", LAST.minusDays(7), 3, "1.00", "3.00"));
        lines.add(line("c", "Pain", "Fournil", LAST.minusDays(14), 3, "1.00", "3.00"));

        RepurchaseArticleResponse bread = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertNumber("10.00", bread.spent());
        assertNumber("3.3", bread.averageQuantity());
        assertNumber("3.33", bread.averageSpend());
        assertNumber("14.29", bread.monthlyEstimate());
    }

    @Test
    @DisplayName("the name shown is the spelling written most often, and the smallest when several are written as often")
    void spellingShownIsTheMostFrequent() {
        String[] spellings = {"Lait", "lait", "LAIT", "LaIt", "lAIT", "laIT", "LAit", "LAIt"};
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < spellings.length; i++) {
            lines.add(line("s" + i, spellings[i], "Lidl", LAST.minusDays(7L * i), 1, "1.00", "1.00"));
        }

        assertEquals("LAIT", RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0).name());

        lines.add(line("x1", "lait", "Lidl", LAST, 1, "1.00", "1.00"));
        lines.add(line("x2", "lait", "Lidl", LAST.minusDays(7), 1, "1.00", "1.00"));

        assertEquals("lait", RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0).name());
    }

    @Test
    @DisplayName("only the first 100 articles are listed and the others are counted")
    void onlyTheFirstHundredArticlesAreListed() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 101; i++) {
            lines.addAll(weekly(String.format("Produit %03d", i), "Lidl", 4));
        }

        RepurchaseResponse response = RepurchaseCalculator.overview(lines, LAST.plusDays(1), 12);

        assertEquals(101, response.articleCount());
        assertEquals(100, response.articles().size());
        assertEquals(1, response.hiddenCount());
    }

    @Test
    @DisplayName("a missing list, a missing line and a line without a date are ignored")
    void missingDataIsIgnored() {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>(weekly(MILK, "Lidl", 6));
        lines.add(null);
        lines.add(line("n", MILK, "Lidl", null, 1, "1.00", "1.00"));
        lines.add(line("m", null, "Lidl", LAST, 1, "1.00", "1.00"));
        lines.add(line("f", MILK, "Lidl", LAST.plusDays(1), 1, "1.00", "1.00"));

        RepurchaseArticleResponse milk = RepurchaseCalculator.overview(lines, LAST, 12).articles().get(0);

        assertEquals(6, milk.purchaseDays());
        assertEquals(0, RepurchaseCalculator.overview(null, LAST, 12).articleCount());
    }

    @Test
    @DisplayName("every amount has two decimals")
    void amountsHaveTwoDecimals() {
        RepurchaseResponse response = RepurchaseCalculator.overview(weekly(MILK, "Lidl", 6), LAST, 12);
        RepurchaseArticleResponse milk = response.articles().get(0);

        assertEquals(2, response.estimatedMonthly().scale());
        assertEquals(2, milk.spent().scale());
        assertEquals(2, milk.averageSpend().scale());
        assertEquals(2, milk.monthlyEstimate().scale());
        assertEquals(1, milk.averageQuantity().scale());

        RepurchaseDetailResponse detail = RepurchaseCalculator.detail(weekly(MILK, "Lidl", 6), MILK, LAST, 12);

        assertEquals(2, detail.purchases().get(0).amount().scale());
    }
}
