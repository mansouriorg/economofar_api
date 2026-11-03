package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * The business tickets (the ones marked as professional) of one period of the saved tickets.
 *
 * period is month, quarter, year or previous-year, and from and to are the ISO dates of its first
 * and last day taken into account (to is today, except for the previous year).
 *
 * ticketCount and totalAmount cover the business tickets. allTicketCount and allAmount cover every
 * saved ticket of the period, and share is totalAmount as a percentage of allAmount, with one
 * decimal (null when nothing was spent). vatTotal adds the VAT of the business tickets and
 * deductibleVat the VAT of the deductible ones. deductibleCount and deductibleAmount are the
 * business tickets marked as deductible, reimbursableCount and reimbursableAmount the ones marked as
 * reimbursable.
 */
public record BusinessExpensesResponse(
        String period,
        String from,
        String to,
        int ticketCount,
        BigDecimal totalAmount,
        int allTicketCount,
        BigDecimal allAmount,
        BigDecimal share,
        BigDecimal vatTotal,
        BigDecimal deductibleVat,
        int deductibleCount,
        BigDecimal deductibleAmount,
        int reimbursableCount,
        BigDecimal reimbursableAmount,
        BusinessSectionResponse reports,
        BusinessSectionResponse projects,
        BusinessSectionResponse clients
) {
}
