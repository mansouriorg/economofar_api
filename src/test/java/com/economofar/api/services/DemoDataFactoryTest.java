package com.economofar.api.services;

import com.economofar.api.services.DemoDataFactory.BudgetSpec;
import com.economofar.api.services.DemoDataFactory.IncomeSpec;
import com.economofar.api.services.DemoDataFactory.LineSpec;
import com.economofar.api.services.DemoDataFactory.ReceiptSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DemoDataFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static final Set<String> CATEGORIES = Set.of(
            "Alimentation", "Restaurant", "Transport", "Sant\u00e9", "Shopping", "Maison", "Loisirs", "Autre"
    );

    private static final Set<String> PAYMENTS = Set.of("Carte", "Esp\u00e8ces", "Apple Pay");

    private static void assertValid(List<ReceiptSpec> receipts, LocalDate today) {
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime oldest = today.minusMonths(DemoDataFactory.MONTHS).atStartOfDay();
        LocalDateTime previous = null;

        for (ReceiptSpec receipt : receipts) {
            String label = receipt.merchant() + " " + receipt.purchaseDate();

            assertTrue(receipt.purchaseDate().isBefore(startOfToday), "in the future: " + label);
            assertFalse(receipt.purchaseDate().isBefore(oldest), "too old: " + label);
            assertTrue(previous == null || previous.isAfter(receipt.purchaseDate()) == false, "not sorted: " + label);
            assertTrue(receipt.total().signum() > 0, "no amount: " + label);
            assertFalse(receipt.lines().isEmpty(), "no line: " + label);
            assertTrue(CATEGORIES.contains(receipt.category()), "category: " + label);
            assertTrue(receipt.paymentMethod() == null || PAYMENTS.contains(receipt.paymentMethod()), "payment: " + label);

            BigDecimal sum = BigDecimal.ZERO;

            for (LineSpec line : receipt.lines()) {
                assertTrue(line.quantity() > 0, "quantity: " + label);
                assertTrue(line.unitPrice().signum() > 0, "price: " + label);
                assertEquals(2, line.unitPrice().scale(), "scale: " + label);
                sum = sum.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            }

            assertEquals(0, sum.compareTo(receipt.total()), "total: " + label);
            previous = receipt.purchaseDate();
        }
    }

    @Test
    @DisplayName("the same day always gives the same data")
    void sameDayGivesTheSameData() {
        assertEquals(DemoDataFactory.receipts(TODAY), DemoDataFactory.receipts(TODAY));
        assertEquals(DemoDataFactory.incomes(TODAY), DemoDataFactory.incomes(TODAY));
    }

    @Test
    @DisplayName("the receipts cover fourteen months and none is dated after yesterday")
    void receiptsCoverFourteenMonths() {
        List<ReceiptSpec> receipts = DemoDataFactory.receipts(TODAY);

        assertTrue(receipts.size() >= 400 && receipts.size() <= 600, "receipts: " + receipts.size());
        assertTrue(receipts.get(0).purchaseDate().toLocalDate().isBefore(TODAY.minusMonths(13)));
        assertTrue(receipts.get(receipts.size() - 1).purchaseDate().toLocalDate().isAfter(TODAY.minusDays(10)));
        assertValid(receipts, TODAY);
    }

    @Test
    @DisplayName("every day of a year gives valid data, with a business receipt from the last few days")
    void everyDayOfAYearGivesValidData() {
        for (int day = 0; day < 366; day++) {
            LocalDate today = LocalDate.of(2027, 1, 1).plusDays(day);
            List<ReceiptSpec> receipts = DemoDataFactory.receipts(today);
            LocalDate newestBusiness = null;

            assertValid(receipts, today);

            for (ReceiptSpec receipt : receipts) {
                if (receipt.business()) {
                    newestBusiness = receipt.purchaseDate().toLocalDate();
                }
            }

            assertNotNull(newestBusiness, "no business receipt on " + today);
            assertFalse(newestBusiness.isBefore(today.minusDays(2)), "no recent business receipt on " + today);
        }
    }

    @Test
    @DisplayName("a different day gives different dates but the same kind of data")
    void aDifferentDayMovesTheDates() {
        List<ReceiptSpec> now = DemoDataFactory.receipts(TODAY);
        List<ReceiptSpec> later = DemoDataFactory.receipts(TODAY.plusDays(60));

        assertTrue(now.equals(later) == false);
        assertTrue(Math.abs(now.size() - later.size()) < 60);
    }

    @Test
    @DisplayName("there are many shops, categories and payment methods")
    void thereIsVariety() {
        List<ReceiptSpec> receipts = DemoDataFactory.receipts(TODAY);
        Set<String> shops = new HashSet<>();
        Set<String> categories = new HashSet<>();
        Set<String> payments = new HashSet<>();

        for (ReceiptSpec receipt : receipts) {
            shops.add(receipt.merchant());
            categories.add(receipt.category());

            if (receipt.paymentMethod() != null) {
                payments.add(receipt.paymentMethod());
            }
        }

        assertTrue(shops.size() >= 20, "shops: " + shops.size());
        assertTrue(categories.containsAll(Set.of("Alimentation", "Restaurant", "Transport", "Sant\u00e9", "Shopping", "Maison", "Loisirs")));
        assertEquals(PAYMENTS, payments);
    }

    @Test
    @DisplayName("business receipts have a project, a client, a report and VAT; personal receipts have none")
    void businessReceiptsAreComplete() {
        int business = 0;

        for (ReceiptSpec receipt : DemoDataFactory.receipts(TODAY)) {
            if (receipt.business()) {
                business++;
                assertNotNull(receipt.project());
                assertNotNull(receipt.client());
                assertNotNull(receipt.expenseReport());
                assertTrue(receipt.expenseReport().startsWith("EXP-"));
                assertTrue(receipt.deductible());
                assertTrue(receipt.vat().signum() > 0);
                assertTrue(receipt.vat().compareTo(receipt.total()) < 0);
            } else {
                assertNull(receipt.project());
                assertNull(receipt.client());
                assertNull(receipt.vat());
            }
        }

        assertTrue(business >= 8, "business receipts: " + business);
    }

    @Test
    @DisplayName("the warranties are after the purchase, some run out soon and some are over")
    void warrantiesCoverEveryState() {
        int soon = 0;
        int expired = 0;
        int running = 0;

        for (ReceiptSpec receipt : DemoDataFactory.receipts(TODAY)) {
            if (receipt.warrantyDate() == null) {
                continue;
            }

            assertTrue(receipt.warrantyDate().isAfter(receipt.purchaseDate().toLocalDate()));

            if (receipt.warrantyDate().isBefore(TODAY)) {
                expired++;
            } else if (receipt.warrantyDate().isBefore(TODAY.plusDays(60))) {
                soon++;
            } else {
                running++;
            }
        }

        assertEquals(1, soon);
        assertEquals(1, expired);
        assertEquals(2, running);
    }

    @Test
    @DisplayName("the demo has a few receipts with problems for the quality screen")
    void imperfectReceiptsExist() {
        List<ReceiptSpec> receipts = DemoDataFactory.receipts(TODAY);
        int unknown = 0;
        int other = 0;
        int missingPayment = 0;
        Set<String> seen = new HashSet<>();
        int duplicates = 0;

        for (ReceiptSpec receipt : receipts) {
            if (receipt.merchant().startsWith("Commer")) {
                unknown++;
            }

            if (receipt.category().equals("Autre")) {
                other++;
            }

            if (receipt.paymentMethod() == null) {
                missingPayment++;
            }

            String key = receipt.merchant() + "|" + receipt.total() + "|" + receipt.purchaseDate().toLocalDate();

            if (seen.add(key) == false) {
                duplicates++;
            }
        }

        assertEquals(1, unknown);
        assertEquals(1, other);
        assertEquals(1, duplicates);
        assertTrue(missingPayment >= 2, "missing payment methods: " + missingPayment);
    }

    @Test
    @DisplayName("the incomes and the budgets are complete")
    void incomesAndBudgets() {
        List<IncomeSpec> incomes = DemoDataFactory.incomes(TODAY);
        int recurring = 0;

        for (IncomeSpec income : incomes) {
            assertTrue(income.amount().signum() > 0);
            assertFalse(income.startDate().isAfter(TODAY));

            if (income.recurring()) {
                recurring++;
                assertEquals(1, income.startDate().getDayOfMonth());
                assertNull(income.endDate());
            }
        }

        assertEquals(1, recurring);
        assertTrue(incomes.size() >= 3);

        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (BudgetSpec budget : DemoDataFactory.categoryBudgets()) {
            assertTrue(budget.monthlyAmount().signum() > 0);
            assertTrue(names.add(budget.category()), "twice: " + budget.category());
            total = total.add(budget.monthlyAmount());
        }

        assertEquals(7, names.size());
        assertTrue(total.compareTo(DemoDataFactory.MONTHLY_BUDGET) <= 0);
    }

    @Test
    @DisplayName("the demo account has a usable e-mail address and password")
    void credentials() {
        assertTrue(DemoDataFactory.EMAIL.matches("[^@\\s]+@[^@\\s]+\\.[a-z]{2,}"));
        assertTrue(DemoDataFactory.PASSWORD.length() >= 8);
        assertEquals("EUR", DemoDataFactory.CURRENCY);
    }
}
