package com.economofar.api.services;

import com.economofar.api.dtos.CashFlowMonthResponse;
import com.economofar.api.dtos.CashFlowResponse;
import com.economofar.api.models.Income;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.IncomeRepository;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the declared incomes and the saved tickets of the last months side by side.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private final IncomeRepository incomeRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public CashFlowResponse report(User user, int requestedMonths) {
        int months = Math.min(Math.max(requestedMonths, CashFlowCalculator.MIN_MONTHS), CashFlowCalculator.MAX_MONTHS);
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(months - 1L);

        List<Income> incomes = incomeRepository.findByUserOrderByStartDateDescIdDesc(user);

        List<CashFlowCalculator.Source> sources = new ArrayList<>();

        for (Income income : incomes) {
            sources.add(new CashFlowCalculator.Source(
                    income.getAmount(),
                    income.getStartDate(),
                    income.isRecurring(),
                    income.getEndDate()
            ));
        }

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                first.atDay(1).atStartOfDay(),
                current.atEndOfMonth().atTime(LocalTime.MAX)
        );

        List<CashFlowCalculator.Spend> spends = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            spends.add(new CashFlowCalculator.Spend(
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        CashFlowCalculator.Report report = CashFlowCalculator.compute(sources, spends, current, months);

        List<CashFlowMonthResponse> rows = new ArrayList<>();

        for (CashFlowCalculator.MonthFlow flow : report.months()) {
            rows.add(new CashFlowMonthResponse(
                    flow.month().toString(),
                    flow.income(),
                    flow.expenses(),
                    flow.net(),
                    flow.savingsRate(),
                    flow.current()
            ));
        }

        return new CashFlowResponse(
                rows,
                report.averageIncome(),
                report.averageExpenses(),
                report.averageNet(),
                report.averageSavingsRate(),
                report.completedMonths(),
                !incomes.isEmpty()
        );
    }
}
