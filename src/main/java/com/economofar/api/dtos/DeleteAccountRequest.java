package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of POST /api/account/delete
 * The password is required to confirm the deletion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {
    private String password;
}
