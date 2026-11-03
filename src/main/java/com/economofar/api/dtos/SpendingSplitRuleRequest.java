package com.economofar.api.dtos;

/**
 * Where a category goes in the split of the spending: group is "NEED", "WANT" or "EXCLUDED", or
 * "DEFAULT" to go back to the proposed classification. category is the key of a category as the split
 * gives it (any spelling of the name is read the same way).
 */
public record SpendingSplitRuleRequest(String category, String group) {
}
