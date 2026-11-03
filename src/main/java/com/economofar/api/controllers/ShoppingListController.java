package com.economofar.api.controllers;

import com.economofar.api.dtos.ShoppingItemRequest;
import com.economofar.api.dtos.ShoppingItemUpdateRequest;
import com.economofar.api.dtos.ShoppingListResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET    /api/shopping-list          -> the list, priced, with the items to buy again
 * POST   /api/shopping-list          -> add an item (the quantity is added when it is already there)
 * PATCH  /api/shopping-list/{id}     -> change the quantity and/or the "bought" flag
 * DELETE /api/shopping-list/{id}     -> remove an item
 * DELETE /api/shopping-list/checked  -> remove every item already bought
 *
 * Every call answers with the whole list.
 */
@RestController
@RequestMapping("/api/shopping-list")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    @GetMapping
    public ResponseEntity<ShoppingListResponse> get(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shoppingListService.get(user));
    }

    @PostMapping
    public ResponseEntity<ShoppingListResponse> add(
            @AuthenticationPrincipal User user,
            @RequestBody ShoppingItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shoppingListService.add(user, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShoppingListResponse> update(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @RequestBody ShoppingItemUpdateRequest request
    ) {
        return ResponseEntity.ok(shoppingListService.update(user, id, request));
    }

    @DeleteMapping("/checked")
    public ResponseEntity<ShoppingListResponse> clearChecked(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shoppingListService.clearChecked(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ShoppingListResponse> remove(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(shoppingListService.remove(user, id));
    }
}
