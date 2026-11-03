package com.economofar.api.services;

import com.economofar.api.dtos.PriceTrackerResponse;
import com.economofar.api.dtos.PriceTrendResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the items of the saved tickets of the last 12 months and returns how their prices moved.
 */
@Service
@RequiredArgsConstructor
public class PriceTrackerService {

    private static final int LOOKBACK_MONTHS = 12;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public PriceTrackerResponse track(User user) {
        LocalDate today = LocalDate.now();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateAfterOrderByPurchaseDateAsc(
                user,
                ScanStatus.SAVED,
                today.minusMonths(LOOKBACK_MONTHS).atStartOfDay()
        );

        List<PriceTrendDetector.Observation> observations = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getItems() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today)) {
                continue;
            }

            for (TicketItem item : ticket.getItems()) {
                BigDecimal unitPrice = unitPriceOf(item);

                if (unitPrice == null) {
                    continue;
                }

                observations.add(new PriceTrendDetector.Observation(
                        item.getName(),
                        ticket.getMerchantName(),
                        unitPrice,
                        date
                ));
            }
        }

        List<PriceTrendResponse> items = PriceTrendDetector.analyze(observations);

        int rising = 0;
        int falling = 0;
        int stable = 0;

        for (PriceTrendResponse item : items) {
            if ("up".equals(item.trend())) {
                rising++;
            } else if ("down".equals(item.trend())) {
                falling++;
            } else {
                stable++;
            }
        }

        return new PriceTrackerResponse(items, rising, falling, stable);
    }

    /**
     * Price of one unit: the unit price when it is known, otherwise the line total divided
     * by the quantity. Returns null when no usable positive price exists.
     */
    static BigDecimal unitPriceOf(TicketItem item) {
        BigDecimal unitPrice = item.getUnitPrice();

        if (unitPrice != null) {
            BigDecimal rounded = unitPrice.setScale(2, RoundingMode.HALF_UP);

            if (rounded.signum() > 0) {
                return rounded;
            }
        }

        BigDecimal total = item.getTotalPrice();
        Integer quantity = item.getQuantity();

        if (total != null && total.signum() > 0 && quantity != null && quantity > 0) {
            BigDecimal perUnit = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);

            if (perUnit.signum() > 0) {
                return perUnit;
            }
        }

        return null;
    }
}
