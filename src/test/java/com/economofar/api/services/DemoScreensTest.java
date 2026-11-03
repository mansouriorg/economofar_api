package com.economofar.api.services;

import com.economofar.api.dtos.BudgetTrackingResponse;
import com.economofar.api.dtos.CategoryProfilesResponse;
import com.economofar.api.dtos.CategorySuggestionsResponse;
import com.economofar.api.dtos.CategoryTrendsResponse;
import com.economofar.api.dtos.MonthCompareResponse;
import com.economofar.api.dtos.SpendingRhythmResponse;
import com.economofar.api.dtos.SpendingSplitResponse;
import com.economofar.api.dtos.YearReviewResponse;
import com.economofar.api.services.DemoDataFactory.BudgetSpec;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the other analyses of the app on the data of the demo account, to make sure that these
 * screens have something to show when someone opens the demo.
 */
@Tag("unit")
class DemoScreensTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static final List<ReceiptSpec> RECEIPTS = DemoDataFactory.receipts(TODAY);

    @Test
    @DisplayName("category trends: the main categories are followed over six months")
    void categoryTrends() {
        List<CategoryTrendsCalculator.Entry> entries = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            entries.add(new CategoryTrendsCalculator.Entry(receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()));
        }

        CategoryTrendsResponse response = CategoryTrendsCalculator.analyse(entries, TODAY, 6);

        assertEquals(6, response.months());
        assertTrue(response.trackedMonths() >= 5, "tracked months: " + response.trackedMonths());
        assertTrue(response.categoryCount() >= 6, "categories: " + response.categoryCount());
        assertNotNull(response.overall());
    }

    @Test
    @DisplayName("category profiles: every category has a profile")
    void categoryProfiles() {
        List<CategoryProfilesCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);
            entries.add(new CategoryProfilesCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        CategoryProfilesResponse response = CategoryProfilesCalculator.analyse(entries, TODAY, 12);

        assertTrue(response.ticketCount() >= 300, "tickets: " + response.ticketCount());
        assertTrue(response.categoryCount() >= 7, "categories: " + response.categoryCount());
        assertEquals(response.categoryCount(), response.categories().size());
    }

    @Test
    @DisplayName("category suggestions: the receipt in the category Autre is found")
    void categorySuggestions() {
        List<CategorySuggestionsCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);
            entries.add(new CategorySuggestionsCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        CategorySuggestionsResponse response = CategorySuggestionsCalculator.analyse(entries, TODAY, 12);

        assertTrue(response.uncategorizedCount() >= 1, "uncategorized: " + response.uncategorizedCount());
        assertEquals(response.groupCount(), response.groups().size());
    }

    @Test
    @DisplayName("month comparison: this month is compared with the last one")
    void monthCompare() {
        List<MonthCompareCalculator.Entry> entries = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            entries.add(new MonthCompareCalculator.Entry(receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()));
        }

        MonthCompareResponse response = MonthCompareCalculator.analyse(entries, TODAY, YearMonth.from(TODAY));

        assertTrue(response.currentTotal().signum() > 0);
        assertTrue(response.previousTotal().signum() > 0);
        assertTrue(response.categoryCount() >= 5, "categories: " + response.categoryCount());
        assertTrue(response.currentTickets() > 10 && response.previousTickets() > 10);
        assertTrue(response.points().isEmpty() == false);
    }

    @Test
    @DisplayName("rhythm: the weeks have a pattern, with weekdays, weekends and streaks")
    void spendingRhythm() {
        List<SpendingRhythmCalculator.Purchase> purchases = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            purchases.add(new SpendingRhythmCalculator.Purchase(receipt.total(), receipt.purchaseDate().toLocalDate()));
        }

        SpendingRhythmResponse response = SpendingRhythmCalculator.analyse(purchases, TODAY, 26);

        assertTrue(response.enoughHistory());
        assertEquals(7, response.weekdays().size());
        assertTrue(response.activeDays() >= 100, "active days: " + response.activeDays());
        assertTrue(response.dailyAverage().signum() > 0);
        assertTrue(response.weekendTotal().signum() > 0);
        assertTrue(response.longestStreak() >= 1, "longest streak: " + response.longestStreak());
        assertNotNull(response.biggestDayDate());
    }

    @Test
    @DisplayName("needs, wants and savings: last month is split and compared with the income")
    void spendingSplit() {
        List<SpendingSplitCalculator.Entry> entries = new ArrayList<>();

        for (ReceiptSpec receipt : RECEIPTS) {
            entries.add(new SpendingSplitCalculator.Entry(receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()));
        }

        BigDecimal salary = BigDecimal.ZERO;

        for (IncomeSpec income : DemoDataFactory.incomes(TODAY)) {
            if (income.recurring()) {
                salary = income.amount();
            }
        }

        SpendingSplitResponse response = SpendingSplitCalculator.analyse(entries, Map.of(), salary, TODAY, "2026-09");

        assertEquals("2026-09", response.month());
        assertTrue(response.hasIncome());
        assertTrue(response.spent().signum() > 0);
        assertTrue(response.ticketCount() > 10, "tickets: " + response.ticketCount());
        assertTrue(response.categoryCount() >= 4, "categories: " + response.categoryCount());
        assertTrue(response.groups().isEmpty() == false);
    }

    @Test
    @DisplayName("year review: this year and last year both have a review")
    void yearReview() {
        List<YearReviewCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);
            entries.add(new YearReviewCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        YearReviewResponse thisYear = YearReviewCalculator.analyse(entries, TODAY, 2026);
        YearReviewResponse lastYear = YearReviewCalculator.analyse(entries, TODAY, 2025);

        assertTrue(thisYear.partial());
        assertTrue(thisYear.total().signum() > 0 && thisYear.tickets() > 200, "tickets: " + thisYear.tickets());
        assertTrue(thisYear.merchantCount() >= 15, "shops: " + thisYear.merchantCount());
        assertTrue(thisYear.categoryCount() >= 7, "categories: " + thisYear.categoryCount());
        assertNotNull(thisYear.biggestTicket());
        assertTrue(lastYear.total().signum() > 0 && lastYear.tickets() > 50, "last year tickets: " + lastYear.tickets());
    }

    @Test
    @DisplayName("budgets: the month is followed against the seven budgets by category")
    void budgetTracking() {
        List<BudgetTrackingCalculator.Entry> entries = new ArrayList<>();

        for (int i = 0; i < RECEIPTS.size(); i++) {
            ReceiptSpec receipt = RECEIPTS.get(i);
            entries.add(new BudgetTrackingCalculator.Entry(
                    DemoInputs.id(i), receipt.merchant(), receipt.category(), receipt.total(), receipt.purchaseDate().toLocalDate()
            ));
        }

        List<BudgetTrackingCalculator.Budget> budgets = new ArrayList<>();

        for (BudgetSpec budget : DemoDataFactory.categoryBudgets()) {
            budgets.add(new BudgetTrackingCalculator.Budget(budget.category(), budget.monthlyAmount()));
        }

        BudgetTrackingResponse response = BudgetTrackingCalculator.analyse(entries, budgets, TODAY, "2026-09");

        assertEquals("2026-09", response.month());
        assertEquals(7, response.budgets().size());
        assertEquals(0, new BigDecimal("1120.00").compareTo(response.budgetTotal()), "total: " + response.budgetTotal());
        assertTrue(response.ticketCount() > 10, "tickets: " + response.ticketCount());
        assertTrue(response.monthSpent().signum() > 0);
        assertNotNull(response.percentUsed());
    }
}
