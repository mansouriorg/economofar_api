package com.economofar.api.services;

import com.economofar.api.dtos.TicketHistoryResponse;
import com.economofar.api.dtos.TicketItemResponse;
import com.economofar.api.dtos.TicketMonthlyReportResponse;
import com.economofar.api.models.User;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketExportService {

    private final TicketHistoryService ticketHistoryService;

    /**
     * CSV export with all filters and intelligent search.
     */
    public byte[] exportCsv(
            User user,
            String search,
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
        List<TicketHistoryResponse> tickets = ticketHistoryService.searchHistory(
                user,
                search,
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

        return buildCsv(tickets);
    }

    /**
     * PDF export with all filters and intelligent search.
     */
    public byte[] exportPdf(
            User user,
            String search,
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
        List<TicketHistoryResponse> tickets = ticketHistoryService.searchHistory(
                user,
                search,
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

        String title = "Economofar - Export des tickets";
        String subtitle = buildSubtitle(fromDate, toDate, month, search);

        return buildPdf(tickets, title, subtitle);
    }

    /**
     * Monthly CSV report.
     *
     * month format:
     * 2026-06
     */
    public byte[] exportMonthlyCsv(User user, String month) {
        validateMonth(month);

        List<TicketHistoryResponse> tickets = ticketHistoryService.searchHistory(
                user,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                month,
                null,
                "NEWEST_FIRST"
        );

        return buildCsv(tickets);
    }

    /**
     * Monthly PDF report.
     *
     * month format:
     * 2026-06
     */
    public byte[] exportMonthlyPdf(User user, String month) {
        validateMonth(month);

        List<TicketHistoryResponse> tickets = ticketHistoryService.searchHistory(
                user,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                month,
                null,
                "NEWEST_FIRST"
        );

        String title = "Economofar - Rapport mensuel";
        String subtitle = formatMonthTitle(month);

        return buildPdf(tickets, title, subtitle);
    }

    /**
     * Monthly report summary as JSON.
     */
    public TicketMonthlyReportResponse getMonthlyReport(User user, String month) {
        validateMonth(month);

        List<TicketHistoryResponse> tickets = ticketHistoryService.searchHistory(
                user,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                month,
                null,
                "NEWEST_FIRST"
        );

        BigDecimal total = tickets.stream()
                .map(TicketHistoryResponse::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = BigDecimal.ZERO;

        if (!tickets.isEmpty()) {
            average = total.divide(
                    BigDecimal.valueOf(tickets.size()),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        String topMerchant = tickets.stream()
                .filter(ticket -> ticket.getMerchantName() != null && !ticket.getMerchantName().isBlank())
                .collect(Collectors.groupingBy(
                        ticket -> ticket.getMerchantName().trim(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Comparator.comparingLong(entry -> entry.getValue()))
                .map(entry -> entry.getKey())
                .orElse("Non disponible");

        BigDecimal highestReceiptAmount = tickets.stream()
                .map(TicketHistoryResponse::getTotalAmount)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new TicketMonthlyReportResponse(
                month,
                formatMonthTitle(month),
                tickets.size(),
                total,
                average,
                topMerchant,
                highestReceiptAmount,
                tickets
        );
    }

    private byte[] buildCsv(List<TicketHistoryResponse> tickets) {
        StringBuilder csv = new StringBuilder();

        csv.append('\uFEFF');
        csv.append("Date,Merchant,Category,Payment Method,Amount,Scan Status,Items,OCR Text\n");

        for (TicketHistoryResponse ticket : tickets) {
            csv.append(escapeCsv(formatDate(ticket.getPurchaseDate()))).append(",");
            csv.append(escapeCsv(ticket.getMerchantName())).append(",");
            csv.append(escapeCsv(ticket.getCategory())).append(",");
            csv.append(escapeCsv(ticket.getPaymentMethod())).append(",");
            csv.append(escapeCsv(formatAmountPlain(ticket.getTotalAmount()))).append(",");
            csv.append(escapeCsv(ticket.getScanStatus() != null ? ticket.getScanStatus().name() : "")).append(",");
            csv.append(escapeCsv(formatItems(ticket.getItems()))).append(",");
            csv.append(escapeCsv(ticket.getRawOcrText())).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildPdf(
            List<TicketHistoryResponse> tickets,
            String title,
            String subtitle
    ) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, outputStream);

            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font subtitleFont = new Font(Font.HELVETICA, 11, Font.NORMAL);
            Font smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);

            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(8);
            document.add(titleParagraph);

            if (subtitle != null && !subtitle.isBlank()) {
                Paragraph subtitleParagraph = new Paragraph(subtitle, subtitleFont);
                subtitleParagraph.setAlignment(Element.ALIGN_CENTER);
                subtitleParagraph.setSpacingAfter(12);
                document.add(subtitleParagraph);
            }

            BigDecimal total = tickets.stream()
                    .map(TicketHistoryResponse::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Paragraph summary = new Paragraph(
                    "Nombre de tickets: " + tickets.size() + "    Total: " + formatAmount(total),
                    subtitleFont
            );
            summary.setSpacingAfter(14);
            document.add(summary);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.3f, 2.2f, 1.5f, 1.5f, 1.2f, 1.3f, 3.5f});

            addHeaderCell(table, "Date", headerFont);
            addHeaderCell(table, "Marchand", headerFont);
            addHeaderCell(table, "Catégorie", headerFont);
            addHeaderCell(table, "Paiement", headerFont);
            addHeaderCell(table, "Montant", headerFont);
            addHeaderCell(table, "Statut", headerFont);
            addHeaderCell(table, "Articles", headerFont);

            for (TicketHistoryResponse ticket : tickets) {
                addBodyCell(table, formatDate(ticket.getPurchaseDate()), smallFont);
                addBodyCell(table, safe(ticket.getMerchantName()), smallFont);
                addBodyCell(table, safe(ticket.getCategory()), smallFont);
                addBodyCell(table, safe(ticket.getPaymentMethod()), smallFont);
                addBodyCell(table, formatAmount(ticket.getTotalAmount()), smallFont);
                addBodyCell(table, ticket.getScanStatus() != null ? ticket.getScanStatus().name() : "", smallFont);
                addBodyCell(table, formatItems(ticket.getItems()), smallFont);
            }

            document.add(table);
            document.close();

            return outputStream.toByteArray();

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF export failed: " + exception.getMessage()
            );
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(new Color(31, 94, 255));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String buildSubtitle(
            String fromDate,
            String toDate,
            String month,
            String search
    ) {
        if (month != null && !month.isBlank()) {
            return "Période: " + formatMonthTitle(month);
        }

        if (fromDate != null && !fromDate.isBlank() && toDate != null && !toDate.isBlank()) {
            return "Période: " + fromDate + " à " + toDate;
        }

        if (search != null && !search.isBlank()) {
            return "Recherche: " + search;
        }

        return "Export généré par Economofar";
    }

    private void validateMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Month is required. Expected format: yyyy-MM, example: 2026-06."
            );
        }

        try {
            YearMonth.parse(month.trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid month format. Expected yyyy-MM, example: 2026-06."
            );
        }
    }

    private String formatMonthTitle(String month) {
        try {
            YearMonth yearMonth = YearMonth.parse(month.trim());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    Locale.FRENCH
            );

            String value = yearMonth.atDay(1).format(formatter);

            if (value.isBlank()) {
                return month;
            }

            return value.substring(0, 1).toUpperCase() + value.substring(1);

        } catch (Exception exception) {
            return month;
        }
    }

    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }

        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        return formatter.format(amount);
    }

    private String formatAmountPlain(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }

        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatItems(List<TicketItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    String name = safe(item.getName());
                    Integer quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    BigDecimal totalPrice = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                    return name + " x" + quantity + " (" + formatAmountPlain(totalPrice) + ")";
                })
                .collect(Collectors.joining(" | "));
    }

    private String escapeCsv(String value) {
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

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}