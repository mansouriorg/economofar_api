package com.economofar.api.services;

import com.economofar.api.dtos.ItemPriceDetailResponse;
import com.economofar.api.dtos.ItemPriceResponse;
import com.economofar.api.dtos.ItemPricesResponse;
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
class ItemPricesCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static ItemPricesCalculator.Line line(String name, String shop, LocalDate date, Integer quantity, String unit, String total) {
        return new ItemPricesCalculator.Line(
                name + date + shop, name, shop, date, quantity,
                unit == null ? null : new BigDecimal(unit),
                total == null ? null : new BigDecimal(total)
        );
    }

    private static List<ItemPricesCalculator.Line> prices(String name, String shop, String... unitPrices) {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < unitPrices.length; i++) {
            lines.add(line(name, shop, TODAY.minusDays(10L * (unitPrices.length - i)), 1, unitPrices[i], null));
        }

        return lines;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static ItemPriceResponse only(ItemPricesResponse response) {
        assertEquals(1, response.items().size());

        return response.items().get(0);
    }

    @Test
    @DisplayName("two names are the same article whatever the capitals, the accents and the spaces")
    void itemKeyIgnoresCapitalsAccentsAndSpaces() {
        assertEquals("lait demi-ecreme 1l", ItemPricesCalculator.itemKey("Lait demi-\u00e9cr\u00e9m\u00e9 1L"));
        assertEquals("lait demi-ecreme 1l", ItemPricesCalculator.itemKey("  LAIT   DEMI-ECREME   1l "));
        assertEquals("", ItemPricesCalculator.itemKey(null));
    }

    @Test
    @DisplayName("the number of months is kept between 3 and 24")
    void monthsAreClamped() {
        assertEquals(3, ItemPricesCalculator.clampMonths(0));
        assertEquals(12, ItemPricesCalculator.clampMonths(12));
        assertEquals(24, ItemPricesCalculator.clampMonths(99));
    }

    @Test
    @DisplayName("a price that goes up by more than 2 percent is rising")
    void risingPrice() {
        ItemPriceResponse item = only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "1.00", "1.05", "1.10", "1.20"), TODAY, 12));

        assertEquals("RISING", item.trend());
        assertMoney("1.00", item.firstPrice());
        assertMoney("1.20", item.lastPrice());
        assertMoney("0.20", item.change());
        assertMoney("20.0", item.changePercent());
        assertMoney("1.00", item.lowestPrice());
        assertMoney("1.20", item.highestPrice());
        assertEquals(4, item.dayCount());
    }

    @Test
    @DisplayName("a price that goes down by more than 2 percent is falling")
    void fallingPrice() {
        ItemPriceResponse item = only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "2.00", "1.90", "1.50"), TODAY, 12));

        assertEquals("FALLING", item.trend());
        assertMoney("-25.0", item.changePercent());
    }

    @Test
    @DisplayName("a price that moves by less than 2 percent is steady")
    void steadyPrice() {
        ItemPriceResponse item = only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "2.00", "2.02", "2.01"), TODAY, 12));

        assertEquals("STEADY", item.trend());
    }

    @Test
    @DisplayName("an article bought on one day only is not followed")
    void articleBoughtOnceIsNotFollowed() {
        ItemPricesResponse response = ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "2.00"), TODAY, 12);

        assertEquals(0, response.trackedCount());
        assertEquals(1, response.onceCount());
    }

    @Test
    @DisplayName("the same article in two shops is compared even when it was bought on one day")
    void shopsAreCompared() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "Monoprix", TODAY.minusDays(7), 5, "2.00", null));
        lines.add(line("Lait", "Lidl", TODAY.minusDays(7), 5, "1.50", null));

        ItemPriceResponse item = only(ItemPricesCalculator.analyse(lines, TODAY, 12));

        assertEquals(2, item.merchantCount());
        assertEquals("Lidl", item.cheapestMerchant());
        assertMoney("1.50", item.cheapestPrice());
        assertMoney("33.3", item.gapPercent());
        assertMoney("2.50", item.potentialSaving());
    }

    @Test
    @DisplayName("the unit price of a line without one is its total divided by its quantity")
    void unitPriceFromTheTotal() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Eau", "Lidl", TODAY.minusDays(20), 3, null, "6.00"));
        lines.add(line("Eau", "Lidl", TODAY.minusDays(10), 3, null, "7.50"));

        ItemPriceResponse item = only(ItemPricesCalculator.analyse(lines, TODAY, 12));

        assertMoney("2.00", item.firstPrice());
        assertMoney("2.50", item.lastPrice());
    }

    @Test
    @DisplayName("lines without a price, a line named Article and lines outside the period are ignored")
    void ignoredLines() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>(prices("Caf\u00e9", "Lidl", "2.00", "2.50"));
        lines.add(line("Caf\u00e9", "Lidl", TODAY.minusDays(5), 1, null, null));
        lines.add(line("Article", "Lidl", TODAY.minusDays(4), 1, "9.00", null));
        lines.add(line("Article", "Lidl", TODAY.minusDays(3), 1, "9.50", null));
        lines.add(line("Caf\u00e9", "Lidl", TODAY.minusYears(5), 1, "0.10", null));

        ItemPricesResponse response = ItemPricesCalculator.analyse(lines, TODAY, 12);
        ItemPriceResponse item = only(response);

        assertEquals("Caf\u00e9", item.name());
        assertEquals(2, item.dayCount());
        assertMoney("2.00", item.firstPrice());
    }

    @Test
    @DisplayName("the answer does not depend on the order of the lines")
    void orderOfTheLinesDoesNotMatter() {
        List<ItemPricesCalculator.Line> lines = DemoInputs.itemPriceLines(DemoDataFactory.receipts(TODAY));
        List<ItemPricesCalculator.Line> shuffled = new ArrayList<>(lines);
        Collections.reverse(shuffled);
        Collections.rotate(shuffled, 31);

        assertEquals(ItemPricesCalculator.analyse(lines, TODAY, 12), ItemPricesCalculator.analyse(shuffled, TODAY, 12));
    }

    @Test
    @DisplayName("the detail lists the prices by day and by shop")
    void detailOfAnItem() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "Monoprix", TODAY.minusDays(30), 1, "1.20", null));
        lines.add(line("Lait", "Lidl", TODAY.minusDays(20), 1, "1.00", null));
        lines.add(line("Lait", "Lidl", TODAY.minusDays(10), 1, "1.10", null));

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(lines, "LAIT", TODAY, 12);

        assertNotNull(detail);
        assertEquals("lait", detail.item().key());
        assertEquals(2, detail.merchantTotal());
        assertEquals(2, detail.merchants().size());
        assertTrue(detail.merchants().stream().anyMatch(merchant -> merchant.name().equals("Lidl") && merchant.cheapest()));
        assertEquals(3, detail.points().size());
        assertEquals(TODAY.minusDays(30).toString(), detail.points().get(0).date());
    }

    @Test
    @DisplayName("the detail of an article that is not followed is null")
    void detailOfAnUnknownItemIsNull() {
        assertNull(ItemPricesCalculator.detail(prices("Caf\u00e9", "Lidl", "2.00", "2.50"), "Th\u00e9", TODAY, 12));
        assertNull(ItemPricesCalculator.detail(prices("Caf\u00e9", "Lidl", "2.00"), "Caf\u00e9", TODAY, 12));
    }

    @Test
    @DisplayName("the unit price comes from the unit price, or else from the total divided by the quantity")
    void priceOfALine() {
        assertMoney("1.26", ItemPricesCalculator.priceOf(1, new BigDecimal("1.255"), null));
        assertMoney("2.00", ItemPricesCalculator.priceOf(3, null, new BigDecimal("6.00")));
        assertMoney("2.00", ItemPricesCalculator.priceOf(3, new BigDecimal("0.00"), new BigDecimal("6.00")));
        assertMoney("6.00", ItemPricesCalculator.priceOf(null, null, new BigDecimal("6.00")));
        assertMoney("6.00", ItemPricesCalculator.priceOf(0, null, new BigDecimal("6.00")));
        assertMoney("6.00", ItemPricesCalculator.priceOf(-2, null, new BigDecimal("6.00")));
        assertNull(ItemPricesCalculator.priceOf(1, null, null));
        assertNull(ItemPricesCalculator.priceOf(1, new BigDecimal("0.004"), null));
        assertNull(ItemPricesCalculator.priceOf(1, null, new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("the tickets are counted once each, and lines without a ticket are not counted")
    void ticketsAreCountedOnce() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(new ItemPricesCalculator.Line("a", "Lait", "Lidl", TODAY.minusDays(20), 1, new BigDecimal("1.00"), null));
        lines.add(new ItemPricesCalculator.Line("a", "Pain", "Lidl", TODAY.minusDays(20), 1, new BigDecimal("1.00"), null));
        lines.add(new ItemPricesCalculator.Line("b", "Lait", "Lidl", TODAY.minusDays(10), 1, new BigDecimal("1.00"), null));
        lines.add(new ItemPricesCalculator.Line(null, "Lait", "Lidl", TODAY.minusDays(5), 1, new BigDecimal("1.00"), null));

        ItemPricesResponse response = ItemPricesCalculator.analyse(lines, TODAY, 12);

        assertEquals(2, response.ticketCount());
        assertEquals(2, response.itemCount());
    }

    @Test
    @DisplayName("the articles are counted by trend and the basket change is weighted by what each article costs")
    void countsAndBasketChange() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.addAll(prices("Alpha", "Lidl", "1.00", "1.20"));
        lines.addAll(prices("Bravo", "Lidl", "2.00", "1.80"));
        lines.addAll(prices("Charlie", "Lidl", "1.00", "1.01"));
        lines.add(line("Delta", "Lidl", TODAY.minusDays(7), 1, "1.00", null));
        lines.add(line("Delta", "Monoprix", TODAY.minusDays(7), 1, "1.50", null));

        ItemPricesResponse response = ItemPricesCalculator.analyse(lines, TODAY, 12);

        assertEquals(4, response.itemCount());
        assertEquals(4, response.trackedCount());
        assertEquals(1, response.risingCount());
        assertEquals(1, response.fallingCount());
        assertEquals(1, response.steadyCount());
        assertEquals(1, response.compareOnlyCount());
        assertEquals(0, response.onceCount());
        assertMoney("1.0", response.basketChangePercent());
        assertEquals(1, response.savingItemCount());
        assertMoney("0.50", response.potentialSaving());
    }

    @Test
    @DisplayName("the same price in two shops is no saving")
    void samePriceIsNoSaving() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "Lidl", TODAY.minusDays(7), 2, "1.00", null));
        lines.add(line("Lait", "Monoprix", TODAY.minusDays(7), 2, "1.00", null));

        ItemPricesResponse response = ItemPricesCalculator.analyse(lines, TODAY, 12);
        ItemPriceResponse item = only(response);

        assertEquals(2, item.merchantCount());
        assertNull(item.cheapestMerchant());
        assertNull(item.cheapestPrice());
        assertNull(item.gapPercent());
        assertMoney("0.00", item.potentialSaving());
        assertEquals(0, response.savingItemCount());
        assertMoney("0.00", response.potentialSaving());

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(lines, "Lait", TODAY, 12);

        assertFalse(detail.merchants().stream().anyMatch(merchant -> merchant.cheapest()));
    }

    @Test
    @DisplayName("only the first 100 articles are listed and the others are counted")
    void onlyTheFirstHundredArticlesAreListed() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 101; i++) {
            lines.addAll(prices(String.format("Produit %03d", i), "Lidl", "1.00", "1.10"));
        }

        ItemPricesResponse response = ItemPricesCalculator.analyse(lines, TODAY, 12);

        assertEquals(101, response.trackedCount());
        assertEquals(100, response.items().size());
        assertEquals(1, response.hiddenCount());
    }

    @Test
    @DisplayName("a plus sign that arrived as a space finds the article only when one article fits")
    void plusSignSentAsASpace() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>(prices("Coca+Cola", "Lidl", "1.00", "1.10"));

        ItemPriceDetailResponse found = ItemPricesCalculator.detail(lines, "Coca Cola", TODAY, 12);

        assertNotNull(found);
        assertEquals("coca+cola", found.item().key());

        lines.addAll(prices("Coca++Cola", "Lidl", "1.00", "1.10"));

        assertNull(ItemPricesCalculator.detail(lines, "Coca Cola", TODAY, 12));

        lines.addAll(prices("Coca Cola", "Lidl", "1.00", "1.10"));

        assertEquals("coca cola", ItemPricesCalculator.detail(lines, "Coca Cola", TODAY, 12).item().key());
    }

    @Test
    @DisplayName("the quantity, the spending, the average price and the lowest and highest prices of an article")
    void totalsOfAnArticle() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Eau", "Lidl", TODAY.minusDays(20), 3, "2.00", null));
        lines.add(line("Eau", "Lidl", TODAY.minusDays(10), 1, "4.00", null));

        ItemPriceResponse item = only(ItemPricesCalculator.analyse(lines, TODAY, 12));

        assertEquals(2, item.purchaseCount());
        assertEquals(2, item.dayCount());
        assertEquals(4, item.quantity());
        assertMoney("10.00", item.spent());
        assertMoney("2.50", item.averagePrice());
        assertMoney("2.00", item.lowestPrice());
        assertMoney("4.00", item.highestPrice());
        assertMoney("2.00", item.change());
        assertMoney("100.0", item.changePercent());
        assertEquals("RISING", item.trend());
        assertEquals(2, item.spent().scale());
        assertEquals(2, item.averagePrice().scale());
    }

    @Test
    @DisplayName("a day with one shop names it, a day with two shops does not")
    void pointsNameTheShopOfTheDay() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "Lidl", TODAY.minusDays(20), 1, "1.00", null));
        lines.add(line("Lait", "Lidl", TODAY.minusDays(10), 1, "1.10", null));
        lines.add(line("Lait", "Monoprix", TODAY.minusDays(10), 1, "1.30", null));

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(lines, "Lait", TODAY, 12);

        assertEquals(2, detail.pointCount());
        assertEquals("Lidl", detail.points().get(0).merchant());
        assertEquals(1, detail.points().get(0).merchantCount());
        assertNull(detail.points().get(1).merchant());
        assertEquals(2, detail.points().get(1).merchantCount());
    }

    @Test
    @DisplayName("the detail shows the last 60 days and counts them all")
    void detailKeepsTheLastSixtyDays() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 65; i++) {
            lines.add(line("Lait", "Lidl", TODAY.minusDays(65 - i), 1, "1.00", null));
        }

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(lines, "Lait", TODAY, 12);

        assertEquals(65, detail.pointCount());
        assertEquals(60, detail.points().size());
        assertEquals(TODAY.minusDays(60).toString(), detail.points().get(0).date());
    }

    @Test
    @DisplayName("a very long name is cut to 120 characters")
    void longNamesAreCut() {
        String key = ItemPricesCalculator.itemKey("b" + "a".repeat(200));

        assertEquals(120, key.length());
        assertTrue(key.startsWith("baaa"));
        assertEquals(key, ItemPricesCalculator.itemKey(key));
    }

    @Test
    @DisplayName("the name shown is the spelling written most often, and the smallest when several are written as often")
    void spellingShownIsTheMostFrequent() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "Lidl", TODAY.minusDays(30), 1, "1.00", null));
        lines.add(line("lait", "Lidl", TODAY.minusDays(20), 1, "1.00", null));
        lines.add(line("LAIT", "Lidl", TODAY.minusDays(10), 1, "1.00", null));

        assertEquals("LAIT", only(ItemPricesCalculator.analyse(lines, TODAY, 12)).name());

        lines.add(line("lait", "Lidl", TODAY.minusDays(5), 1, "1.00", null));

        assertEquals("lait", only(ItemPricesCalculator.analyse(lines, TODAY, 12)).name());
    }

    @Test
    @DisplayName("shops with a one letter name are shops, a missing name is not")
    void oneLetterShopsAreShops() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Lait", "X", TODAY.minusDays(7), 1, "1.00", null));
        lines.add(line("Lait", "Y", TODAY.minusDays(7), 1, "1.50", null));
        lines.add(line("Lait", null, TODAY.minusDays(7), 1, "9.00", null));

        ItemPriceResponse item = only(ItemPricesCalculator.analyse(lines, TODAY, 12));

        assertEquals(2, item.merchantCount());
        assertEquals("X", item.cheapestMerchant());
    }

    @Test
    @DisplayName("an article whose name starts with a plus sign is found without it")
    void plusSignAtTheStart() {
        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(prices("+Cola", "Lidl", "1.00", "1.10"), "Cola", TODAY, 12);

        assertNotNull(detail);
        assertEquals("+cola", detail.item().key());
    }

    @Test
    @DisplayName("a missing list, a missing line and a line without a date are ignored")
    void missingDataIsIgnored() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>(prices("Caf\u00e9", "Lidl", "2.00", "2.10"));
        lines.add(null);
        lines.add(new ItemPricesCalculator.Line("n", "Caf\u00e9", "Lidl", null, 1, new BigDecimal("2.00"), null));

        assertEquals(2, only(ItemPricesCalculator.analyse(lines, TODAY, 12)).dayCount());
        assertEquals(0, ItemPricesCalculator.analyse(null, TODAY, 12).itemCount());
    }

    @Test
    @DisplayName("a line without a quantity, or with a zero one, counts for one article")
    void quantityOfALineIsAtLeastOne() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
        lines.add(line("Eau", "Lidl", TODAY.minusDays(30), null, "2.00", null));
        lines.add(line("Eau", "Lidl", TODAY.minusDays(20), 0, "2.00", null));
        lines.add(line("Eau", "Lidl", TODAY.minusDays(10), -3, "2.00", null));

        ItemPriceResponse item = only(ItemPricesCalculator.analyse(lines, TODAY, 12));

        assertEquals(3, item.quantity());
        assertMoney("6.00", item.spent());
    }

    @Test
    @DisplayName("the change is a percentage with one decimal")
    void changeHasOneDecimal() {
        ItemPriceResponse item = only(ItemPricesCalculator.analyse(prices("Th\u00e9", "Lidl", "3.00", "3.10"), TODAY, 12));

        assertMoney("3.3", item.changePercent());
        assertEquals("RISING", item.trend());
    }

    @Test
    @DisplayName("a change of exactly 2 percent up is rising and of exactly 2 percent down is falling")
    void trendBoundaries() {
        assertEquals("RISING", only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "1.00", "1.02"), TODAY, 12)).trend());
        assertEquals("FALLING", only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "1.00", "0.98"), TODAY, 12)).trend());
        assertEquals("STEADY", only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "1.00", "1.01"), TODAY, 12)).trend());
        assertEquals("STEADY", only(ItemPricesCalculator.analyse(prices("Caf\u00e9", "Lidl", "1.00", "0.99"), TODAY, 12)).trend());
    }

    @Test
    @DisplayName("the detail lists at most 20 shops and counts them all")
    void detailListsTwentyShops() {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < 21; i++) {
            lines.add(line("Lait", String.format("Boutique %02d", i), TODAY.minusDays(7), 1, "1.00", null));
        }

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(lines, "Lait", TODAY, 12);

        assertEquals(21, detail.merchantTotal());
        assertEquals(20, detail.merchants().size());
    }
}
