package com.economofar.api.services;

import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ReceiptPdfService {

    public byte[] buildReceiptPdf(Ticket ticket) {
        if (ticket == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Receipt not found"
            );
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);

            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);

            Paragraph title = new Paragraph("Economofar - Reçu", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(16);
            document.add(title);

            document.add(new Paragraph("Marchand : " + safe(ticket.getMerchantName()), normalFont));
            document.add(new Paragraph("Date : " + formatDate(ticket), normalFont));
            document.add(new Paragraph("Catégorie : " + safe(ticket.getCategory()), normalFont));
            document.add(new Paragraph("Moyen de paiement : " + safe(ticket.getPaymentMethod()), normalFont));
            document.add(new Paragraph("Montant total : " + formatAmount(ticket.getTotalAmount()), normalFont));
            document.add(new Paragraph("Type : " + value(ticket.getUsageType()), normalFont));
            document.add(new Paragraph("Remboursable : " + booleanLabel(ticket.getReimbursable()), normalFont));
            document.add(new Paragraph("Déductible : " + booleanLabel(ticket.getDeductible()), normalFont));
            document.add(new Paragraph("TVA : " + formatAmount(ticket.getVatAmount()), normalFont));
            document.add(new Paragraph("Projet : " + safe(ticket.getProjectName()), normalFont));
            document.add(new Paragraph("Client : " + safe(ticket.getClientName()), normalFont));
            document.add(new Paragraph("Note : " + safe(ticket.getNote()), normalFont));

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Articles", sectionFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1f, 1.4f, 1.4f, 2f, 1.5f});

            addHeader(table, "Nom", headerFont);
            addHeader(table, "Qté", headerFont);
            addHeader(table, "Prix unit.", headerFont);
            addHeader(table, "Total", headerFont);
            addHeader(table, "Catégorie", headerFont);
            addHeader(table, "Type", headerFont);

            List<TicketItem> items = ticket.getItems();

            if (items != null) {
                for (TicketItem item : items) {
                    addCell(table, safe(item.getName()), normalFont);
                    addCell(table, item.getQuantity() != null ? item.getQuantity().toString() : "1", normalFont);
                    addCell(table, formatAmount(item.getUnitPrice()), normalFont);
                    addCell(table, formatAmount(item.getTotalPrice()), normalFont);
                    addCell(table, safe(item.getCategory()), normalFont);
                    addCell(table, value(item.getUsageType()), normalFont);
                }
            }

            document.add(table);

            document.close();

            return outputStream.toByteArray();

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Receipt PDF generation failed: " + exception.getMessage()
            );
        }
    }

    private void addHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(new Color(31, 94, 255));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String formatDate(Ticket ticket) {
        if (ticket.getPurchaseDate() == null) {
            return "";
        }

        return ticket.getPurchaseDate().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        );
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        return NumberFormat
                .getCurrencyInstance(Locale.FRANCE)
                .format(amount);
    }

    private String booleanLabel(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Oui" : "Non";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}