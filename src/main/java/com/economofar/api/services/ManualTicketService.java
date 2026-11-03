package com.economofar.api.services;

import com.economofar.api.dtos.ManualTicketRequest;
import com.economofar.api.dtos.ManualTicketResponse;
import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * Saves a ticket that the user typed by hand. The ticket goes through the same path as a scanned
 * one (TicketService.createTicket), so the unusual spending check, the monthly budget and the
 * category budgets work exactly the same way. A purchase made today gets the current time so that
 * it is sorted with the other tickets of the day; an older day is kept as a plain day.
 */
@Service
@RequiredArgsConstructor
public class ManualTicketService {

    public static final String SOURCE_TEXT = "Saisie manuelle";

    private final TicketService ticketService;

    public ManualTicketResponse create(ManualTicketRequest request, User user) {
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Session expirée. Veuillez vous reconnecter."
            );
        }

        if (request == null) {
            throw new IllegalArgumentException("Les informations du ticket sont obligatoires.");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        ManualTicketValidator.Valid valid = ManualTicketValidator.validate(
                new ManualTicketValidator.Input(
                        request.getMerchantName(),
                        request.getTotalAmount(),
                        request.getPurchaseDate(),
                        request.getCategory(),
                        request.getPaymentMethod(),
                        request.getNote()
                ),
                now.toLocalDate()
        );

        String when = valid.purchaseDate().equals(now.toLocalDate())
                ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(now)
                : valid.purchaseDate().toString();

        TicketRequest ticketRequest = TicketRequest.builder()
                .merchantName(valid.merchantName())
                .totalAmount(valid.totalAmount())
                .purchaseDate(when)
                .category(valid.category())
                .paymentMethod(valid.paymentMethod())
                .note(valid.note())
                .rawOcrText(SOURCE_TEXT)
                .items(new ArrayList<>())
                .build();

        Ticket saved = ticketService.createTicket(ticketRequest, user);

        String day = saved.getPurchaseDate() != null
                ? saved.getPurchaseDate().toLocalDate().toString()
                : valid.purchaseDate().toString();

        return new ManualTicketResponse(
                saved.getId(),
                saved.getMerchantName(),
                saved.getTotalAmount(),
                day,
                saved.getCategory(),
                saved.getPaymentMethod(),
                saved.getNote()
        );
    }
}
