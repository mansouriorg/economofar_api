package com.economofar.api.services;

import com.economofar.api.dtos.BusinessExpensesResponse;
import com.economofar.api.dtos.ItemPriceResponse;
import com.economofar.api.dtos.ItemPricesResponse;
import com.economofar.api.dtos.PaymentUsageResponse;
import com.economofar.api.dtos.QualityIssueCountResponse;
import com.economofar.api.dtos.RepurchaseArticleResponse;
import com.economofar.api.dtos.RepurchaseResponse;
import com.economofar.api.dtos.ShopsResponse;
import com.economofar.api.dtos.SmallPurchasesResponse;
import com.economofar.api.dtos.TicketQualityResponse;
import com.economofar.api.dtos.UnusualPurchasesResponse;
import com.economofar.api.dtos.WarrantyOverviewResponse;
import com.economofar.api.services.DemoDataFactory.IncomeSpec;
import com.economofar.api.services.DemoDataFactory.ReceiptSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs ten of the analyses of the app on the data of the demo account, for one fixed day, to make
 * sure that these screens have something to show when someone opens the demo. DemoScreensTest does
 * the same for eight more. The events screen starts empty: an event is created by the person.
 */
@Tag("unit")
class DemoAnalyticsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static final List<ReceiptSpec> RECEIPTS = DemoDataFactory.receipts(TODAY);

    private static RepurchaseArticleResponse article(RepurchaseResponse response, String key) {
        for (RepurchaseArticleResponse article : response.articles()) {
            if (article.key().equals(key)) {
                return article;
            }
        }

        throw new AssertionError("no article " + key);
    }

    private static ItemPriceResponse item(ItemPricesResponse response, String key) {
        for (ItemPriceResponse item : response.items()) {
            if (item.key().equals(key)) {
                return item;
            }
        }

        throw new AssertionError("no item " + key);
    }

    private static int count(TicketQualityResponse response, String type) {
        for (QualityIssueCountResponse count : response.counts()) {
            if (count.type().equals(type)) {
                return count.count();
            }
        }

        return 0;
    }

    @Test
    @DisplayName("repurchase: the weekly shop gives articles with a seven day cycle")
    void repurchase() {
        RepurchaseResponse response = RepurchaseCalculator.overview(DemoInputs.repurchaseLines(RECEIPTS), TODAY, 12);

        assertTrue(response.articles().size() >= 25, "regular articles: " + response.articles().size());
        assertTrue(response.irregularCount() > 0);
        assertTrue(response.estimatedMonthly().signum() > 0);

        RepurchaseArticleResponse milk = article(response, "lait demi ecreme 1l");

        assertEquals(7, milk.cycleDays());
        assertTrue(milk.purchaseDays() >= 40, "milk purchase days: " + milk.purchaseDays());
        assertTrue(milk.usualShop() != null);

        RepurchaseArticleResponse baguette = article(response, "baguette tradition");

        assertTrue(baguette.cycleDays() >= 3 && baguette.cycleDays() <= 4);
        assertEquals("Boulangerie Le Fournil", baguette.usualShop());

        assertEquals("OK", article(response, "abonnement netflix").status());
        assertEquals(31, article(response, "abonnement netflix").cycleDays());
    }

    @Test
    @DisplayName("item prices: the price of milk rises and Lidl is the cheapest shop")
    void itemPrices() {
        ItemPricesResponse response = ItemPricesCalculator.analyse(DemoInputs.itemPriceLines(RECEIPTS), TODAY, 12);

        assertTrue(response.trackedCount() >= 40, "followed articles: " + response.trackedCount());
        assertTrue(response.risingCount() > 0);
        assertTrue(response.savingItemCount() > 0);
        assertTrue(response.potentialSaving().signum() > 0);

        ItemPriceResponse milk = item(response, "lait demi-ecreme 1l");

        assertEquals("RISING", milk.trend());
        assertTrue(milk.merchantCount() >= 2);
        assertEquals("Lidl", milk.cheapestMerchant());

        ItemPriceResponse video = item(response, "abonnement netflix");

        assertEquals("RISING", video.trend());
        assertEquals(0, new BigDecimal("13.49").compareTo(video.firstPrice()));
        assertEquals(0, new BigDecimal("15.49").compareTo(video.lastPrice()));
    }

    @Test
    @DisplayName("shops: many shops, one unknown ticket and a big share for the biggest ones")
    void shops() {
        ShopsResponse response = ShopsCalculator.overview(DemoInputs.shopRows(RECEIPTS), TODAY, 12);

        assertTrue(response.shopCount() >= 20, "shops: " + response.shopCount());
        assertEquals(1, response.unknownTicketCount());
        assertTrue(response.hasBaseline());
        assertTrue(response.topShopsSharePercent().compareTo(BigDecimal.valueOf(25)) > 0);
        assertTrue(response.upCount() + response.downCount() > 0);
    }

    @Test
    @DisplayName("quality: the demo has one unknown shop, one missing category and one duplicate")
    void quality() {
        List<TicketQualityCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);

            entries.add(new TicketQualityCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        TicketQualityResponse response = TicketQualityCalculator.analyse(entries, TODAY, 12);

        assertEquals(1, count(response, TicketQualityCalculator.UNKNOWN_MERCHANT));
        assertEquals(1, count(response, TicketQualityCalculator.NO_CATEGORY));
        assertTrue(count(response, TicketQualityCalculator.DUPLICATE) >= 1);
        assertEquals(0, count(response, TicketQualityCalculator.MISSING_AMOUNT));
        assertEquals(0, count(response, TicketQualityCalculator.FUTURE_DATE));
        assertTrue(response.flaggedCount() >= 3 && response.flaggedCount() <= 5, "flagged: " + response.flaggedCount());
    }

    @Test
    @DisplayName("warranties: one runs out soon, one is over and two are running")
    void warranties() {
        List<WarrantyOverviewCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);

            entries.add(new WarrantyOverviewCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.note(), receipt.total(),
                    receipt.purchaseDate().toLocalDate(), receipt.warrantyDate()
            ));
        }

        WarrantyOverviewResponse response = WarrantyOverviewCalculator.analyse(entries, TODAY, 30);

        assertEquals(1, response.soonCount());
        assertEquals("Boulanger", response.soon().get(0).merchant());
        assertEquals(1, response.expiredCount());
        assertEquals(2, response.activeCount());
        assertTrue(response.protectedAmount().compareTo(BigDecimal.valueOf(1500)) > 0);
    }

    @Test
    @DisplayName("business: the year has reports, projects and two clients")
    void business() {
        List<BusinessExpensesCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);

            entries.add(new BusinessExpensesCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.total(), receipt.purchaseDate().toLocalDate(),
                    receipt.business(), receipt.reimbursable(), receipt.deductible(), receipt.vat(),
                    receipt.project(), receipt.client(), receipt.expenseReport()
            ));
        }

        BusinessExpensesResponse response = BusinessExpensesCalculator.analyse(entries, TODAY, "year");

        assertTrue(response.ticketCount() >= 6, "business tickets: " + response.ticketCount());
        assertTrue(response.allTicketCount() > response.ticketCount());
        assertEquals(2, response.clients().groupCount());
        assertEquals(2, response.projects().groupCount());
        assertTrue(response.reports().groupCount() >= 5);
        assertTrue(response.vatTotal().signum() > 0);
        assertTrue(response.reimbursableCount() > 0);
        assertEquals(0, response.clients().missingCount());
    }

    @Test
    @DisplayName("payment methods: card, cash and mobile payment, and some tickets without a method")
    void paymentMethods() {
        List<PaymentUsageCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);

            entries.add(new PaymentUsageCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.paymentMethod(),
                    receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        PaymentUsageResponse response = PaymentUsageCalculator.analyse(entries, TODAY, 12);

        assertEquals(3, response.methodCount());
        assertTrue(response.unspecifiedCount() >= 1);
        assertEquals("Carte", response.methods().get(0).name());
    }

    @Test
    @DisplayName("small purchases: coffee and bread add up")
    void smallPurchases() {
        List<SmallPurchasesCalculator.Entry> entries = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            entries.add(new SmallPurchasesCalculator.Entry(
                    receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        SmallPurchasesResponse response = SmallPurchasesCalculator.analyse(entries, TODAY, 12, 10);

        assertTrue(response.smallCount() >= 100, "small purchases: " + response.smallCount());
        assertTrue(response.smallTotal().signum() > 0);
        assertTrue(response.yearlyEstimate().signum() > 0);
        assertTrue(response.merchantCount() >= 2);
    }

    @Test
    @DisplayName("unusual purchases: the dinner for two stands out in the last 90 days")
    void unusualPurchases() {
        List<UnusualPurchaseCalculator.Purchase> purchases = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            purchases.add(new UnusualPurchaseCalculator.Purchase(
                    receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        UnusualPurchasesResponse response = UnusualPurchaseCalculator.analyse(purchases, TODAY, 90);

        assertTrue(response.flaggedCount() >= 1, "flagged: " + response.flaggedCount());
        assertTrue(response.purchases().stream().anyMatch(purchase -> purchase.merchant().equals("Restaurant Le Jardin")));
    }

    @Test
    @DisplayName("cash flow: the income is above the spending in most months")
    void cashFlow() {
        List<CashFlowCalculator.Source> sources = new ArrayList<>();

        for (IncomeSpec income : DemoDataFactory.incomes(TODAY)) {
            sources.add(new CashFlowCalculator.Source(income.amount(), income.startDate(), income.recurring(), income.endDate()));
        }

        List<CashFlowCalculator.Spend> spends = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            spends.add(new CashFlowCalculator.Spend(receipt.total(), receipt.purchaseDate().toLocalDate()));
        }

        CashFlowCalculator.Report report = CashFlowCalculator.compute(sources, spends, YearMonth.from(TODAY), 12);

        assertEquals(12, report.months().size());
        assertTrue(report.completedMonths() >= 11);
        assertNotNull(report.averageSavingsRate());
        assertTrue(report.averageSavingsRate() > 0.0 && report.averageSavingsRate() < 70.0, "savings rate: " + report.averageSavingsRate());

        int saving = 0;

        for (CashFlowCalculator.MonthFlow month : report.months()) {
            if (month.net().signum() > 0) {
                saving++;
            }
        }

        assertTrue(saving >= 8, "months with savings: " + saving);
    }
}
