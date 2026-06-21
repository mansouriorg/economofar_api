package com.economofar.api.services;

import com.economofar.api.dtos.TicketHistoryGroupResponse;
import com.economofar.api.dtos.TicketHistoryResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TicketHistoryService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> searchHistory(
            User user,
            String fromDate,
            String toDate,
            String merchant,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String category,
            String paymentMethod,
            String month,
            String scanStatus,
            String sortBy
    ) {
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        Specification<Ticket> specification = buildSpecification(
                user,
                fromDate,
                toDate,
                merchant,
                minAmount,
                maxAmount,
                category,
                paymentMethod,
                month,
                scanStatus
        );

        Sort sort = buildSort(sortBy);

        return ticketRepository.findAll(specification, sort)
                .stream()
                .map(TicketHistoryResponse::fromTicket)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryGroupResponse> getGroupedHistory(
            User user,
            String groupBy,
            String fromDate,
            String toDate,
            String merchant,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String category,
            String paymentMethod,
            String month,
            String scanStatus,
            String sortBy
    ) {
        List<TicketHistoryResponse> tickets = searchHistory(
                user,
                fromDate,
                toDate,
                merchant,
                minAmount,
                maxAmount,
                category,
                paymentMethod,
                month,
                scanStatus,
                sortBy
        );

        String resolvedGroupBy = normalize(groupBy);

        if (resolvedGroupBy == null || resolvedGroupBy.isBlank()) {
            resolvedGroupBy = "MONTH";
        }

        Map<String, List<TicketHistoryResponse>> grouped = new LinkedHashMap<>();

        for (TicketHistoryResponse ticket : tickets) {
            String key;

            if ("MERCHANT".equals(resolvedGroupBy)) {
                key = getMerchantGroupKey(ticket);
            } else {
                key = getMonthGroupKey(ticket);
            }

            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(ticket);
        }

        List<TicketHistoryGroupResponse> response = new ArrayList<>();

        for (Map.Entry<String, List<TicketHistoryResponse>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<TicketHistoryResponse> groupTickets = entry.getValue();

            BigDecimal groupTotal = groupTickets
                    .stream()
                    .map(TicketHistoryResponse::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String title;

            if ("MERCHANT".equals(resolvedGroupBy)) {
                title = key;
            } else {
                title = formatMonthTitle(key);
            }

            response.add(
                    new TicketHistoryGroupResponse(
                            key,
                            title,
                            groupTickets.size(),
                            groupTotal,
                            groupTickets
                    )
            );
        }

        return response;
    }

    private Specification<Ticket> buildSpecification(
            User user,
            String fromDate,
            String toDate,
            String merchant,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String category,
            String paymentMethod,
            String month,
            String scanStatus
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("user"), user));

            LocalDateTime parsedFromDate = parseStartDate(fromDate);
            LocalDateTime parsedToDate = parseEndDate(toDate);

            if (parsedFromDate != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("purchaseDate"),
                                parsedFromDate
                        )
                );
            }

            if (parsedToDate != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("purchaseDate"),
                                parsedToDate
                        )
                );
            }

            if (merchant != null && !merchant.isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("merchantName")),
                                "%" + merchant.trim().toLowerCase() + "%"
                        )
                );
            }

            if (minAmount != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("totalAmount"),
                                minAmount
                        )
                );
            }

            if (maxAmount != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("totalAmount"),
                                maxAmount
                        )
                );
            }

            if (category != null && !category.isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(root.get("category")),
                                category.trim().toLowerCase()
                        )
                );
            }

            if (paymentMethod != null && !paymentMethod.isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(root.get("paymentMethod")),
                                paymentMethod.trim().toLowerCase()
                        )
                );
            }

            YearMonth parsedMonth = parseMonth(month);

            if (parsedMonth != null) {
                LocalDateTime startOfMonth = parsedMonth.atDay(1).atStartOfDay();
                LocalDateTime endOfMonth = parsedMonth.atEndOfMonth().atTime(LocalTime.MAX);

                predicates.add(
                        criteriaBuilder.between(
                                root.get("purchaseDate"),
                                startOfMonth,
                                endOfMonth
                        )
                );
            }

            ScanStatus parsedScanStatus = parseScanStatus(scanStatus);

            if (parsedScanStatus != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("scanStatus"),
                                parsedScanStatus
                        )
                );
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(String sortBy) {
        String value = normalize(sortBy);

        if (value == null || value.isBlank()) {
            value = "NEWEST_FIRST";
        }

        return switch (value) {
            case "OLDEST_FIRST" -> Sort.by(Sort.Order.asc("purchaseDate"));
            case "HIGHEST_AMOUNT" -> Sort.by(Sort.Order.desc("totalAmount"));
            case "LOWEST_AMOUNT" -> Sort.by(Sort.Order.asc("totalAmount"));
            case "MERCHANT_NAME" -> Sort.by(Sort.Order.asc("merchantName"));
            case "NEWEST_FIRST" -> Sort.by(Sort.Order.desc("purchaseDate"));
            default -> Sort.by(Sort.Order.desc("purchaseDate"));
        };
    }

    private LocalDateTime parseStartDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed);
        } catch (Exception ignored) {
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid fromDate format. Expected yyyy-MM-dd or ISO datetime."
        );
    }

    private LocalDateTime parseEndDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            return LocalDate.parse(trimmed).atTime(LocalTime.MAX);
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed);
        } catch (Exception ignored) {
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid toDate format. Expected yyyy-MM-dd or ISO datetime."
        );
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return YearMonth.parse(value.trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid month format. Expected yyyy-MM, example: 2026-06."
            );
        }
    }

    private ScanStatus parseScanStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return ScanStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid scanStatus. Expected: UPLOADING, ANALYZING, OCR_COMPLETED, NEEDS_REVIEW, SAVED or FAILED."
            );
        }
    }

    private String getMonthGroupKey(TicketHistoryResponse ticket) {
        if (ticket.getPurchaseDate() == null) {
            return "unknown";
        }

        return YearMonth.from(ticket.getPurchaseDate()).toString();
    }

    private String getMerchantGroupKey(TicketHistoryResponse ticket) {
        if (ticket.getMerchantName() == null || ticket.getMerchantName().isBlank()) {
            return "Magasin inconnu";
        }

        return ticket.getMerchantName().trim();
    }

    private String formatMonthTitle(String key) {
        if (key == null || key.equals("unknown")) {
            return "Date inconnue";
        }

        try {
            YearMonth yearMonth = YearMonth.parse(key);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    Locale.FRENCH
            );

            String title = yearMonth.atDay(1).format(formatter);

            if (title.isBlank()) {
                return key;
            }

            return title.substring(0, 1).toUpperCase() + title.substring(1);

        } catch (Exception exception) {
            return key;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        return value.trim().toUpperCase();
    }
}