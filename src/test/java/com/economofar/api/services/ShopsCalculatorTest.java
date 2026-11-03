package com.economofar.api.services;

import com.economofar.api.dtos.ShopDetailResponse;
import com.economofar.api.dtos.ShopMonthResponse;
import com.economofar.api.dtos.ShopResponse;
import com.economofar.api.dtos.ShopsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ShopsCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate RECENT = TODAY.minusDays(10);
    private static final LocalDate PREVIOUS = TODAY.minusDays(120);
    private static final LocalDate OLD = TODAY.minusDays(250);

    private static ShopsCalculator.Row row(String shop, String category, String amount, LocalDate date) {
        return new ShopsCalculator.Row(
                shop + date,
                shop,
                category,
                amount == null ? null : new BigDecimal(amount),
                date
        );
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static ShopResponse shop(ShopsResponse response, String key) {
        for (ShopResponse shop : response.shops()) {
            if (shop.key().equals(key)) {
                return shop;
            }
        }

        throw new AssertionError("no shop " + key + " in " + response.shops());
    }

    private static List<ShopsCalculator.Row> history() {
        return new ArrayList<>(List.of(
                row("Base", "Maison", "5.00", OLD),
                row("Base", "Maison", "5.00", OLD.minusDays(3)),
                row("Base", "Maison", "5.00", OLD.minusDays(6))
        ));
    }

    private static ShopResponse trend(String previous, String recent) {
        List<ShopsCalculator.Row> rows = history();

        if (previous != null) {
            rows.add(row("Trend", "Alimentation", previous, PREVIOUS));
        }

        if (recent != null) {
            rows.add(row("Trend", "Alimentation", recent, RECENT));
        }

        return shop(ShopsCalculator.overview(rows, TODAY, 12), "trend");
    }

    @Test
    @DisplayName("two names are the same shop whatever the capitals, the accents and the punctuation")
    void shopKeyIgnoresCapitalsAccentsAndPunctuation() {
        assertEquals("cafe de la gare", ShopsCalculator.shopKey("Caf\u00e9 de la Gare"));
        assertEquals("cafe de la gare", ShopsCalculator.shopKey("  CAFE, DE LA   GARE  "));
        assertEquals("h m", ShopsCalculator.shopKey("H&M"));
    }

    @Test
    @DisplayName("the number of months is kept between 3 and 24")
    void monthsAreClamped() {
        assertEquals(3, ShopsCalculator.clampMonths(1));
        assertEquals(12, ShopsCalculator.clampMonths(12));
        assertEquals(24, ShopsCalculator.clampMonths(40));
    }

    @Test
    @DisplayName("the shops are listed with the most spending first, with their share, visits and average ticket")
    void shopsAreRankedBySpending() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "10.00", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Alimentation", "20.00", LocalDate.of(2026, 10, 10)),
                row("Lidl", "Alimentation", "40.00", LocalDate.of(2026, 10, 17)),
                row("Monoprix", "Alimentation", "60.00", LocalDate.of(2026, 10, 12))
        );

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertEquals(4, response.ticketCount());
        assertEquals(2, response.shopCount());
        assertMoney("130.00", response.totalSpent());
        assertEquals(2, response.shops().size());

        ShopResponse first = response.shops().get(0);

        assertEquals("lidl", first.key());
        assertEquals("Lidl", first.name());
        assertMoney("70.00", first.spent());
        assertMoney("53.8", first.sharePercent());
        assertEquals(3, first.ticketCount());
        assertEquals(3, first.visitDays());
        assertMoney("23.33", first.averageBasket());
        assertEquals("2026-10-03", first.firstDate());
        assertEquals("2026-10-17", first.lastDate());
        assertEquals(6, first.daysSinceLast());
        assertEquals("monoprix", response.shops().get(1).key());
    }

    @Test
    @DisplayName("tickets without a known shop only count in the totals")
    void unknownShopsOnlyCountInTheTotals() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)),
                row("Commer\u00e7ant inconnu", "Alimentation", "12.40", LocalDate.of(2026, 10, 4)),
                row("  ", "Alimentation", "5.00", LocalDate.of(2026, 10, 5)),
                row(null, "Alimentation", "1.00", LocalDate.of(2026, 10, 6))
        );

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertEquals(1, response.shopCount());
        assertEquals(4, response.ticketCount());
        assertMoney("48.40", response.totalSpent());
        assertMoney("18.40", response.unknownSpent());
        assertEquals(3, response.unknownTicketCount());
    }

    @Test
    @DisplayName("tickets in the future or before the history are ignored")
    void futureAndAncientTicketsAreIgnored() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Alimentation", "99.00", TODAY.plusDays(1)),
                row("Lidl", "Alimentation", "99.00", LocalDate.of(2020, 1, 1)),
                row("Lidl", "Alimentation", "99.00", null)
        );

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertEquals(1, response.ticketCount());
        assertMoney("30.00", response.totalSpent());
    }

    @Test
    @DisplayName("a ticket without an amount is still a visit")
    void ticketWithoutAmountIsAVisit() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Alimentation", null, LocalDate.of(2026, 10, 4))
        );

        ShopResponse lidl = shop(ShopsCalculator.overview(rows, TODAY, 12), "lidl");

        assertEquals(2, lidl.ticketCount());
        assertEquals(2, lidl.visitDays());
        assertMoney("30.00", lidl.spent());
    }

    @Test
    @DisplayName("a person who has just started has no new shop")
    void noNewShopWithoutAHistory() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", TODAY.minusDays(10)),
                row("Monoprix", "Alimentation", "20.00", TODAY.minusDays(5))
        );

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertFalse(response.hasBaseline());
        assertEquals(0, response.newCount());
    }

    @Test
    @DisplayName("a shop first seen in the last 90 days is new once there is a history")
    void newShopWithAHistory() {
        List<ShopsCalculator.Row> rows = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            rows.add(row("Lidl", "Alimentation", "30.00", TODAY.minusDays(120 + 7L * i)));
        }

        rows.add(row("Lidl", "Alimentation", "30.00", TODAY.minusDays(10)));
        rows.add(row("Biocoop", "Alimentation", "45.00", TODAY.minusDays(8)));

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertTrue(response.hasBaseline());
        assertEquals(1, response.newCount());
        assertEquals("NEW", shop(response, "biocoop").status());
    }

    @Test
    @DisplayName("the answer does not depend on the order of the rows")
    void orderOfTheRowsDoesNotMatter() {
        List<ShopsCalculator.Row> rows = new ArrayList<>(DemoInputs.shopRows(DemoDataFactory.receipts(TODAY)));
        List<ShopsCalculator.Row> shuffled = new ArrayList<>(rows);
        Collections.reverse(shuffled);
        Collections.rotate(shuffled, 17);

        assertEquals(ShopsCalculator.overview(rows, TODAY, 12), ShopsCalculator.overview(shuffled, TODAY, 12));
    }

    @Test
    @DisplayName("the detail of a shop gives the months, the categories, the weekdays and the tickets")
    void detailOfAShop() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 9, 5)),
                row("Lidl", "Alimentation", "20.00", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Maison", "10.00", LocalDate.of(2026, 10, 10)),
                row("Monoprix", "Alimentation", "60.00", LocalDate.of(2026, 10, 12))
        );

        ShopDetailResponse detail = ShopsCalculator.detail(rows, "LIDL", TODAY, 12);

        assertNotNull(detail);
        assertEquals("lidl", detail.shop().key());
        assertMoney("60.00", detail.shop().spent());
        assertEquals(7, detail.weekdayVisitDays().size());
        assertEquals(3, detail.weekdayVisitDays().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, detail.tickets().size());
        assertEquals("2026-10-10", detail.tickets().get(0).date());
        assertEquals(2, detail.categoryTotal());
        assertEquals("Alimentation", detail.categories().get(0).name());
        assertMoney("50.00", detail.categories().get(0).spent());
        assertTrue(detail.monthly().stream().anyMatch(month -> month.month().equals("2026-10") && month.ticketCount() == 2));
    }

    @Test
    @DisplayName("the detail of a shop that is not in the period is null")
    void detailOfAnUnknownShopIsNull() {
        List<ShopsCalculator.Row> rows = List.of(row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)));

        assertNull(ShopsCalculator.detail(rows, "Carrefour", TODAY, 12));
        assertNull(ShopsCalculator.detail(rows, "", TODAY, 12));
    }

    @Test
    @DisplayName("a shop where the last 90 days cost a quarter more than the 90 days before is UP")
    void aQuarterMoreIsUp() {
        ShopResponse up = trend("100.00", "125.00");

        assertEquals("UP", up.status());
        assertMoney("25.0", up.changePercent());
        assertMoney("125.00", up.recentSpent());
        assertMoney("100.00", up.previousSpent());
        assertEquals("STEADY", trend("100.00", "124.00").status());
        assertMoney("24.0", trend("100.00", "124.00").changePercent());
    }

    @Test
    @DisplayName("a rise of less than 10 euros is not UP")
    void aSmallRiseIsNotUp() {
        assertEquals("STEADY", trend("20.00", "25.00").status());
        assertEquals("UP", trend("20.00", "30.00").status());
    }

    @Test
    @DisplayName("a shop where the last 90 days cost a quarter less than the 90 days before is DOWN")
    void aQuarterLessIsDown() {
        ShopResponse down = trend("100.00", "75.00");

        assertEquals("DOWN", down.status());
        assertMoney("-25.0", down.changePercent());
        assertEquals("STEADY", trend("100.00", "76.00").status());
    }

    @Test
    @DisplayName("a drop of less than 10 euros is not DOWN")
    void aSmallDropIsNotDown() {
        assertEquals("STEADY", trend("20.00", "15.00").status());
        assertEquals("DOWN", trend("30.00", "20.00").status());
    }

    @Test
    @DisplayName("the change is a percentage with one decimal, and there is none without earlier spending")
    void changeHasOneDecimal() {
        assertMoney("33.3", trend("30.00", "40.00").changePercent());
        assertNull(trend(null, "50.00").changePercent());
    }

    @Test
    @DisplayName("a shop visited on two days before and not since is FADING")
    void fadingShop() {
        List<ShopsCalculator.Row> twoDays = history();
        twoDays.add(row("Trend", "Alimentation", "5.00", PREVIOUS));
        twoDays.add(row("Trend", "Alimentation", "5.00", PREVIOUS.plusDays(3)));

        List<ShopsCalculator.Row> oneDay = history();
        oneDay.add(row("Trend", "Alimentation", "5.00", PREVIOUS));

        assertEquals("FADING", shop(ShopsCalculator.overview(twoDays, TODAY, 12), "trend").status());
        assertEquals("STEADY", shop(ShopsCalculator.overview(oneDay, TODAY, 12), "trend").status());
    }

    @Test
    @DisplayName("a shop is NEW when it was first seen in the last 90 days and 3 days were saved before")
    void newNeedsThreeDaysOfHistory() {
        assertEquals("NEW", trend(null, "20.00").status());

        List<ShopsCalculator.Row> twoDays = history();
        twoDays.remove(2);
        twoDays.add(row("Trend", "Alimentation", "20.00", RECENT));

        ShopsResponse response = ShopsCalculator.overview(twoDays, TODAY, 12);

        assertFalse(response.hasBaseline());
        assertEquals("STEADY", shop(response, "trend").status());
    }

    @Test
    @DisplayName("the history that is looked at stops after 24 months")
    void historyStopsAfterTwentyFourMonths() {
        List<ShopsCalculator.Row> rows = new ArrayList<>();
        rows.add(row("Base", "Maison", "5.00", LocalDate.of(2024, 10, 31)));
        rows.add(row("Base", "Maison", "5.00", LocalDate.of(2024, 11, 1)));
        rows.add(row("Base", "Maison", "5.00", LocalDate.of(2024, 11, 2)));
        rows.add(row("Trend", "Alimentation", "20.00", RECENT));

        assertFalse(ShopsCalculator.overview(rows, TODAY, 3).hasBaseline());
        assertEquals("STEADY", shop(ShopsCalculator.overview(rows, TODAY, 3), "trend").status());

        rows.add(row("Base", "Maison", "5.00", LocalDate.of(2024, 11, 3)));

        assertTrue(ShopsCalculator.overview(rows, TODAY, 3).hasBaseline());
        assertEquals("NEW", shop(ShopsCalculator.overview(rows, TODAY, 3), "trend").status());
    }

    @Test
    @DisplayName("the trend compares 90 days with 90 days whatever the period shown")
    void trendDoesNotDependOnThePeriod() {
        List<ShopsCalculator.Row> rows = history();
        rows.add(row("Trend", "Alimentation", "100.00", PREVIOUS));
        rows.add(row("Trend", "Alimentation", "125.00", RECENT));

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 3);
        ShopResponse trend = shop(response, "trend");

        assertEquals("UP", trend.status());
        assertMoney("100.00", trend.previousSpent());
        assertMoney("125.00", trend.spent());
    }

    @Test
    @DisplayName("the last 90 days start 89 days ago and the 90 days before them 179 days ago")
    void windowsStartOnTheRightDays() {
        List<ShopsCalculator.Row> rows = new ArrayList<>();
        rows.add(row("Edge", "Alimentation", "1.00", TODAY.minusDays(89)));
        rows.add(row("Edge", "Alimentation", "2.00", TODAY.minusDays(90)));
        rows.add(row("Edge", "Alimentation", "4.00", TODAY.minusDays(179)));
        rows.add(row("Edge", "Alimentation", "8.00", TODAY.minusDays(180)));

        ShopResponse edge = shop(ShopsCalculator.overview(rows, TODAY, 12), "edge");

        assertMoney("1.00", edge.recentSpent());
        assertMoney("6.00", edge.previousSpent());
        assertMoney("15.00", edge.spent());
    }

    @Test
    @DisplayName("the shops are counted by status and the three biggest have their share")
    void countsAndTopShare() {
        List<ShopsCalculator.Row> rows = history();
        rows.add(row("Nouveau", "Alimentation", "20.00", RECENT));
        rows.add(row("Lointain", "Alimentation", "5.00", PREVIOUS));
        rows.add(row("Lointain", "Alimentation", "5.00", PREVIOUS.plusDays(3)));
        rows.add(row("Hausse", "Alimentation", "100.00", PREVIOUS));
        rows.add(row("Hausse", "Alimentation", "130.00", RECENT));
        rows.add(row("Baisse", "Alimentation", "100.00", PREVIOUS));
        rows.add(row("Baisse", "Alimentation", "60.00", RECENT));
        rows.add(row("Stable", "Alimentation", "100.00", PREVIOUS));
        rows.add(row("Stable", "Alimentation", "100.00", RECENT));

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertTrue(response.hasBaseline());
        assertEquals(6, response.shopCount());
        assertEquals(1, response.newCount());
        assertEquals(1, response.fadingCount());
        assertEquals(1, response.upCount());
        assertEquals(1, response.downCount());
        assertMoney("635.00", response.totalSpent());
        assertMoney("92.9", response.topShopsSharePercent());
        assertEquals("hausse", response.shops().get(0).key());
    }

    @Test
    @DisplayName("only the first 100 shops are listed and the others are counted")
    void onlyTheFirstHundredShopsAreListed() {
        List<ShopsCalculator.Row> rows = new ArrayList<>();

        for (int i = 0; i < 101; i++) {
            rows.add(row(String.format("Boutique %03d", i), "Maison", "10.00", RECENT));
        }

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertEquals(101, response.shopCount());
        assertEquals(100, response.shops().size());
        assertEquals(1, response.hiddenCount());
    }

    @Test
    @DisplayName("tickets without an amount give no share and no error")
    void ticketsWithoutAnAmountGiveNoShare() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", null, LocalDate.of(2026, 10, 3)),
                row("Lidl", "Alimentation", "0.00", LocalDate.of(2026, 10, 4))
        );

        ShopsResponse response = ShopsCalculator.overview(rows, TODAY, 12);

        assertEquals(2, response.ticketCount());
        assertNull(response.topShopsSharePercent());
        assertNull(response.shops().get(0).sharePercent());

        ShopDetailResponse detail = ShopsCalculator.detail(rows, "Lidl", TODAY, 12);

        assertNotNull(detail);
        assertNull(detail.basketGapPercent());
        assertNull(detail.categories().get(0).sharePercent());
    }

    @Test
    @DisplayName("the detail compares the basket of the shop with the basket of all the named shops")
    void basketIsComparedWithTheOtherShops() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 9, 5)),
                row("Lidl", "Alimentation", "20.00", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Maison", "10.00", LocalDate.of(2026, 10, 10)),
                row("Monoprix", "Alimentation", "60.00", LocalDate.of(2026, 10, 12)),
                row("Commer\u00e7ant inconnu", "Alimentation", "500.00", LocalDate.of(2026, 10, 13))
        );

        ShopDetailResponse lidl = ShopsCalculator.detail(rows, "Lidl", TODAY, 12);
        ShopDetailResponse monoprix = ShopsCalculator.detail(rows, "Monoprix", TODAY, 12);

        assertMoney("30.00", lidl.overallAverageBasket());
        assertMoney("20.00", lidl.shop().averageBasket());
        assertMoney("-33.3", lidl.basketGapPercent());
        assertMoney("100.0", monoprix.basketGapPercent());
        assertMoney("9.7", lidl.shop().sharePercent());
    }

    @Test
    @DisplayName("the days of the week are counted from Monday, one visit day each")
    void weekdaysStartOnMonday() {
        List<ShopsCalculator.Row> rows = List.of(
                row("Lidl", "Alimentation", "10.00", LocalDate.of(2026, 10, 19)),
                row("Lidl", "Alimentation", "10.00", LocalDate.of(2026, 10, 19)),
                row("Lidl", "Alimentation", "10.00", LocalDate.of(2026, 10, 17)),
                row("Lidl", "Alimentation", "10.00", LocalDate.of(2026, 10, 18))
        );

        List<Integer> days = ShopsCalculator.detail(rows, "Lidl", TODAY, 12).weekdayVisitDays();

        assertEquals(List.of(1, 0, 0, 0, 0, 1, 1), days);
    }

    @Test
    @DisplayName("the average gap between two visit days is in days with one decimal, and missing with one visit")
    void averageGapBetweenVisits() {
        List<ShopsCalculator.Row> three = List.of(
                row("Lidl", "Alimentation", "10.00", TODAY.minusDays(50)),
                row("Lidl", "Alimentation", "10.00", TODAY.minusDays(40)),
                row("Lidl", "Alimentation", "10.00", TODAY.minusDays(10))
        );
        List<ShopsCalculator.Row> two = List.of(
                row("Lidl", "Alimentation", "10.00", TODAY.minusDays(20)),
                row("Lidl", "Alimentation", "10.00", TODAY.minusDays(10))
        );
        List<ShopsCalculator.Row> one = List.of(row("Lidl", "Alimentation", "10.00", TODAY.minusDays(10)));

        assertMoney("20.0", shop(ShopsCalculator.overview(three, TODAY, 12), "lidl").averageGapDays());
        assertMoney("10.0", shop(ShopsCalculator.overview(two, TODAY, 12), "lidl").averageGapDays());
        assertNull(shop(ShopsCalculator.overview(one, TODAY, 12), "lidl").averageGapDays());
    }

    @Test
    @DisplayName("a very long name is cut to 120 characters")
    void longNamesAreCut() {
        String key = ShopsCalculator.shopKey("b" + "a".repeat(200));

        assertEquals(120, key.length());
        assertTrue(key.startsWith("baaa"));
        assertEquals(key, ShopsCalculator.shopKey(key));
    }

    @Test
    @DisplayName("every amount has two decimals")
    void amountsHaveTwoDecimals() {
        ShopsResponse response = ShopsCalculator.overview(List.of(
                row("Lidl", "Alimentation", "10.5", LocalDate.of(2026, 10, 3)),
                row("Lidl", "Alimentation", "3", LocalDate.of(2026, 10, 4))
        ), TODAY, 12);

        ShopResponse lidl = response.shops().get(0);

        assertEquals(2, response.totalSpent().scale());
        assertEquals(2, response.unknownSpent().scale());
        assertEquals(2, lidl.spent().scale());
        assertEquals(2, lidl.averageBasket().scale());
        assertEquals(2, lidl.recentSpent().scale());
        assertEquals(2, lidl.previousSpent().scale());
        assertEquals(1, lidl.sharePercent().scale());
    }

    @Test
    @DisplayName("the name shown is the spelling written most often, and the smallest when several are written as often")
    void spellingShownIsTheMostFrequent() {
        List<ShopsCalculator.Row> rows = new ArrayList<>();
        rows.add(row("Lidl", "Alimentation", "10.00", TODAY.minusDays(30)));
        rows.add(row("lidl", "Alimentation", "10.00", TODAY.minusDays(20)));
        rows.add(row("LIDL", "Alimentation", "10.00", TODAY.minusDays(10)));

        assertEquals("LIDL", shop(ShopsCalculator.overview(rows, TODAY, 12), "lidl").name());

        rows.add(row("lidl", "Alimentation", "10.00", TODAY.minusDays(5)));

        assertEquals("lidl", shop(ShopsCalculator.overview(rows, TODAY, 12), "lidl").name());
    }

    @Test
    @DisplayName("a shop that is the only one has a basket equal to the overall basket")
    void aSingleShopIsComparedWithItself() {
        List<ShopsCalculator.Row> rows = List.of(row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)));

        ShopDetailResponse detail = ShopsCalculator.detail(rows, "Lidl", TODAY, 12);

        assertMoney("30.00", detail.overallAverageBasket());
        assertMoney("0.0", detail.basketGapPercent());
    }

    @Test
    @DisplayName("the detail has one line for every month of the period, the empty ones too")
    void everyMonthOfThePeriodIsListed() {
        List<ShopsCalculator.Row> rows = List.of(row("Lidl", "Alimentation", "30.00", LocalDate.of(2026, 10, 3)));

        List<ShopMonthResponse> twelve = ShopsCalculator.detail(rows, "Lidl", TODAY, 12).monthly();

        assertEquals(12, twelve.size());
        assertEquals("2025-11", twelve.get(0).month());
        assertEquals("2025-12", twelve.get(1).month());
        assertEquals("2026-10", twelve.get(11).month());
        assertMoney("0.00", twelve.get(5).spent());
        assertEquals(0, twelve.get(5).ticketCount());
        assertEquals(3, ShopsCalculator.detail(rows, "Lidl", TODAY, 3).monthly().size());
    }
}
