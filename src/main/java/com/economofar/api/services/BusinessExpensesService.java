package com.economofar.api.services;

import com.economofar.api.dtos.BusinessExpensesResponse;
import com.economofar.api.models.ReceiptUsageType;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the saved tickets of the period and asks BusinessExpensesCalculator to add up the business
 * ones. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class BusinessExpensesService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public BusinessExpensesResponse business(User user, String period) {
        return business(user, period, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around a change of period can be tested. */
    public BusinessExpensesResponse business(User user, String period, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        BusinessExpensesCalculator.Window window = BusinessExpensesCalculator.window(today, period);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                window.from().atStartOfDay(),
                window.to().atTime(LocalTime.MAX)
        );

        List<BusinessExpensesCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            entries.add(new BusinessExpensesCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate(),
                    ticket.getUsageType() == ReceiptUsageType.BUSINESS,
                    Boolean.TRUE.equals(ticket.getReimbursable()),
                    Boolean.TRUE.equals(ticket.getDeductible()),
                    ticket.getVatAmount(),
                    ticket.getProjectName(),
                    ticket.getClientName(),
                    ticket.getExpenseReportReference()
            ));
        }

        return BusinessExpensesCalculator.analyse(entries, today, window.period());
    }
}
