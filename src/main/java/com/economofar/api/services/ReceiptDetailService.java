package com.economofar.api.services;

import com.economofar.api.dtos.ReceiptDetailResponse;
import com.economofar.api.dtos.ReceiptDetailUpdateRequest;
import com.economofar.api.dtos.ReceiptItemDetailRequest;
import com.economofar.api.dtos.ReceiptItemDetailResponse;
import com.economofar.api.models.ReceiptUsageType;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptDetailService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public ReceiptDetailResponse getReceiptDetail(UUID receiptId, User user) {
        Ticket ticket = getTicketForUser(receiptId, user);
        return ReceiptDetailResponse.fromTicket(ticket);
    }

    @Transactional
    public ReceiptDetailResponse updateReceiptDetail(
            UUID receiptId,
            ReceiptDetailUpdateRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Receipt update request is required"
            );
        }

        Ticket ticket = getTicketForUser(receiptId, user);

        if (request.getMerchantName() != null) {
            ticket.setMerchantName(nonBlankOrDefault(
                    request.getMerchantName(),
                    "Commerçant inconnu"
            ));
        }

        if (request.getPurchaseDate() != null) {
            ticket.setPurchaseDate(parsePurchaseDate(request.getPurchaseDate()));
        }

        if (request.getTotalAmount() != null) {
            ticket.setTotalAmount(request.getTotalAmount());
        }

        if (request.getCategory() != null) {
            ticket.setCategory(request.getCategory());
        }

        if (request.getPaymentMethod() != null) {
            ticket.setPaymentMethod(request.getPaymentMethod());
        }

        if (request.getRawOcrText() != null) {
            ticket.setRawOcrText(request.getRawOcrText());
        }

        if (request.getImageUrl() != null) {
            ticket.setImageUrl(request.getImageUrl());
        }

        if (request.getNote() != null) {
            ticket.setNote(request.getNote());
        }

        if (request.getWarrantyDate() != null) {
            ticket.setWarrantyDate(parseWarrantyDate(request.getWarrantyDate()));
        }

        if (request.getReimbursable() != null) {
            ticket.setReimbursable(request.getReimbursable());
        }

        if (request.getUsageType() != null) {
            ticket.setUsageType(request.getUsageType());
        }

        if (request.getVatAmount() != null) {
            ticket.setVatAmount(request.getVatAmount());
        }

        if (request.getDeductible() != null) {
            ticket.setDeductible(request.getDeductible());
        }

        if (request.getProjectName() != null) {
            ticket.setProjectName(cleanNullable(request.getProjectName()));
        }

        if (request.getClientName() != null) {
            ticket.setClientName(cleanNullable(request.getClientName()));
        }

        if (request.getExpenseReportReference() != null) {
            ticket.setExpenseReportReference(cleanNullable(request.getExpenseReportReference()));
        }

        Ticket savedTicket = ticketRepository.save(ticket);

        return ReceiptDetailResponse.fromTicket(savedTicket);
    }

    @Transactional
    public void deleteReceipt(UUID receiptId, User user) {
        Ticket ticket = getTicketForUser(receiptId, user);
        ticketRepository.delete(ticket);
    }

    @Transactional
    public ReceiptDetailResponse duplicateReceipt(UUID receiptId, User user) {
        Ticket original = getTicketForUser(receiptId, user);

        Ticket duplicate = Ticket.builder()
                .merchantName(safe(original.getMerchantName()) + " - Copie")
                .totalAmount(original.getTotalAmount())
                .purchaseDate(original.getPurchaseDate() != null ? original.getPurchaseDate() : LocalDateTime.now())
                .category(original.getCategory())
                .paymentMethod(original.getPaymentMethod())
                .imageUrl(original.getImageUrl())
                .rawOcrText(original.getRawOcrText())
                .scanStatus(original.getScanStatus())
                .user(user)
                .build();

        duplicate.setUsageType(original.getUsageType());
        duplicate.setNote(original.getNote());
        duplicate.setWarrantyDate(original.getWarrantyDate());
        duplicate.setReimbursable(original.getReimbursable());
        duplicate.setVatAmount(original.getVatAmount());
        duplicate.setDeductible(original.getDeductible());
        duplicate.setProjectName(original.getProjectName());
        duplicate.setClientName(original.getClientName());
        duplicate.setExpenseReportReference(original.getExpenseReportReference());
        duplicate.setOriginalFileName(original.getOriginalFileName());
        duplicate.setImageContentType(original.getImageContentType());

        if (original.getItems() != null) {
            for (TicketItem originalItem : original.getItems()) {
                TicketItem itemCopy = new TicketItem();
                itemCopy.setName(originalItem.getName());
                itemCopy.setQuantity(originalItem.getQuantity());
                itemCopy.setUnitPrice(originalItem.getUnitPrice());
                itemCopy.setTotalPrice(originalItem.getTotalPrice());
                itemCopy.setCategory(originalItem.getCategory());
                itemCopy.setUsageType(originalItem.getUsageType());

                duplicate.addItem(itemCopy);
            }
        }

        Ticket savedDuplicate = ticketRepository.save(duplicate);

        return ReceiptDetailResponse.fromTicket(savedDuplicate);
    }

    @Transactional
    public ReceiptItemDetailResponse addItem(
            UUID receiptId,
            ReceiptItemDetailRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item request is required"
            );
        }

        Ticket ticket = getTicketForUser(receiptId, user);

        TicketItem item = new TicketItem();
        applyItemRequest(item, request);

        ticket.addItem(item);
        ticket.setTotalAmount(computeItemsTotal(ticket));

        ticketRepository.save(ticket);

        return ReceiptItemDetailResponse.fromItem(item);
    }

    @Transactional
    public ReceiptItemDetailResponse updateItem(
            UUID receiptId,
            UUID itemId,
            ReceiptItemDetailRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item request is required"
            );
        }

        Ticket ticket = getTicketForUser(receiptId, user);
        TicketItem item = findItem(ticket, itemId);

        applyItemRequest(item, request);

        ticket.setTotalAmount(computeItemsTotal(ticket));
        ticketRepository.save(ticket);

        return ReceiptItemDetailResponse.fromItem(item);
    }

    @Transactional
    public void deleteItem(UUID receiptId, UUID itemId, User user) {
        Ticket ticket = getTicketForUser(receiptId, user);
        TicketItem item = findItem(ticket, itemId);

        ticket.removeItem(item);
        ticket.setTotalAmount(computeItemsTotal(ticket));

        ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public byte[] exportAccountantCsv(UUID receiptId, User user) {
        Ticket ticket = getTicketForUser(receiptId, user);

        StringBuilder csv = new StringBuilder();

        csv.append('\uFEFF');
        csv.append("Receipt ID,Merchant,Date,Total,VAT,Deductible,Reimbursable,Usage Type,Project,Client,Expense Report,Item,Quantity,Unit Price,Item Total,Item Category,Item Usage Type\n");

        if (ticket.getItems() == null || ticket.getItems().isEmpty()) {
            appendAccountantLine(csv, ticket, null);
        } else {
            for (TicketItem item : ticket.getItems()) {
                appendAccountantLine(csv, ticket, item);
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendAccountantLine(StringBuilder csv, Ticket ticket, TicketItem item) {
        csv.append(escape(ticket.getId() != null ? ticket.getId().toString() : "")).append(",");
        csv.append(escape(ticket.getMerchantName())).append(",");
        csv.append(escape(ticket.getPurchaseDate() != null ? ticket.getPurchaseDate().toString() : "")).append(",");
        csv.append(escape(formatPlain(ticket.getTotalAmount()))).append(",");
        csv.append(escape(formatPlain(ticket.getVatAmount()))).append(",");
        csv.append(escape(booleanText(ticket.getDeductible()))).append(",");
        csv.append(escape(booleanText(ticket.getReimbursable()))).append(",");
        csv.append(escape(value(ticket.getUsageType()))).append(",");
        csv.append(escape(ticket.getProjectName())).append(",");
        csv.append(escape(ticket.getClientName())).append(",");
        csv.append(escape(ticket.getExpenseReportReference())).append(",");

        if (item != null) {
            csv.append(escape(item.getName())).append(",");
            csv.append(escape(item.getQuantity() != null ? item.getQuantity().toString() : "")).append(",");
            csv.append(escape(formatPlain(item.getUnitPrice()))).append(",");
            csv.append(escape(formatPlain(item.getTotalPrice()))).append(",");
            csv.append(escape(item.getCategory())).append(",");
            csv.append(escape(value(item.getUsageType()))).append("\n");
        } else {
            csv.append(",,,,,,\n");
        }
    }

    private Ticket getTicketForUser(UUID receiptId, User user) {
        if (receiptId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Receipt id is required"
            );
        }

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        return ticketRepository
                .findByIdAndUser(receiptId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Receipt not found"
                ));
    }

    private TicketItem findItem(Ticket ticket, UUID itemId) {
        if (itemId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item id is required"
            );
        }

        if (ticket.getItems() == null || ticket.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Item not found"
            );
        }

        return ticket.getItems()
                .stream()
                .filter(item -> item.getId() != null)
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Item not found"
                ));
    }

    private void applyItemRequest(TicketItem item, ReceiptItemDetailRequest request) {
        String name = request.getName();

        item.setName(
                name != null && !name.isBlank()
                        ? name.trim()
                        : "Article"
        );

        Integer quantity = request.getQuantity();

        item.setQuantity(
                quantity != null && quantity > 0
                        ? quantity
                        : 1
        );

        item.setUnitPrice(request.getUnitPrice());
        item.setTotalPrice(request.getTotalPrice());
        item.setCategory(request.getCategory());

        if (request.getUsageType() != null) {
            item.setUsageType(request.getUsageType());
        } else if (item.getUsageType() == null) {
            item.setUsageType(ReceiptUsageType.PERSONAL);
        }
    }

    private BigDecimal computeItemsTotal(Ticket ticket) {
        if (ticket == null || ticket.getItems() == null || ticket.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return ticket.getItems()
                .stream()
                .map(TicketItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDateTime parsePurchaseDate(String purchaseDate) {
        if (purchaseDate == null || purchaseDate.isBlank()) {
            return LocalDateTime.now();
        }

        String value = purchaseDate.trim();

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
                    .atStartOfDay();
        } catch (Exception ignored) {
        }

        return LocalDateTime.now();
    }

    private LocalDate parseWarrantyDate(String warrantyDate) {
        if (warrantyDate == null || warrantyDate.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(warrantyDate.trim(), DateTimeFormatter.ISO_DATE);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid warrantyDate format. Expected yyyy-MM-dd."
            );
        }
    }

    private String nonBlankOrDefault(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        return fallback;
    }

    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatPlain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String booleanText(Boolean value) {
        return Boolean.TRUE.equals(value) ? "true" : "false";
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();

        if (cleaned.contains(",") || cleaned.contains("\"")) {
            cleaned = cleaned.replace("\"", "\"\"");
            return "\"" + cleaned + "\"";
        }

        return cleaned;
    }
}