package com.economofar.api.dtos;

/** The rule as it is stored: the key of the category and its group ("DEFAULT" when the rule was removed). */
public record SpendingSplitRuleResponse(String category, String group) {
}
