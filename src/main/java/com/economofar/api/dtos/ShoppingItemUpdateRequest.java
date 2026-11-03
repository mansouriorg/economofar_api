package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingItemUpdateRequest {

    private Integer quantity;

    private Boolean checked;
}
