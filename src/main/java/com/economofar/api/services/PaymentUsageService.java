package com.economofar.api.services;

import com.economofar.api.dtos.PaymentUsageResponse;
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
 * Reads the saved tickets of the period and asks PaymentUsageCalculator to add them up per payment
 * method. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class PaymentUsageService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public PaymentUsageResponse usage(User user, Integer months) {
        return usage(user, months, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public PaymentUsageResponse usage(User user, Integer months, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        PaymentUsageCalculator.Window window = PaymentUsageCalculator.window(today, months);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                window.from().atStartOfDay(),
                window.to().atTime(LocalTime.MAX)
        );

        List<PaymentUsageCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            entries.add(new PaymentUsageCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getPaymentMethod(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return PaymentUsageCalculator.analyse(entries, today, window.months());
    }
}
