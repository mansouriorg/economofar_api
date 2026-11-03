package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A ticket of a shop. date is "yyyy-MM-dd". */
public record ShopTicketResponse(String id, String date, String category, BigDecimal amount) {
}
