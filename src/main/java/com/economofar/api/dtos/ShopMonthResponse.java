package com.economofar.api.dtos;

import java.math.BigDecimal;

/** What was spent in a shop during a month. month is "yyyy-MM". */
public record ShopMonthResponse(String month, BigDecimal spent, int ticketCount) {
}
