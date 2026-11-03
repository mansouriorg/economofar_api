package com.economofar.api.controllers;

import com.economofar.api.dtos.ItemPriceDetailResponse;
import com.economofar.api.dtos.ItemPricesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.ItemPricesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/item-prices?months=12
 * -> the articles of the saved tickets whose price can be followed (bought on two different days or in
 * two named shops), the ones with the most spending first. months goes from 3 to 24, 12 when omitted.
 *
 * GET /api/analytics/item-prices/detail?key=lait&months=12
 * -> one article: its figures, the shops from the cheapest to the dearest, and its latest prices. key is
 * the key given by the list. A missing key gives a 400, an article that is not followed a 404.
 */
@RestController
@RequestMapping("/api/analytics/item-prices")
@RequiredArgsConstructor
public class ItemPricesController {

    private final ItemPricesService itemPricesService;

    @GetMapping
    public ResponseEntity<ItemPricesResponse> overview(
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(itemPricesService.overview(user, months));
    }

    @GetMapping("/detail")
    public ResponseEntity<ItemPriceDetailResponse> detail(
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(itemPricesService.detail(user, key, months));
    }
}
