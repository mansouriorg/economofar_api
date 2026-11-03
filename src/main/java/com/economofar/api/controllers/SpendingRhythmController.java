package com.economofar.api.controllers;

import com.economofar.api.dtos.SpendingRhythmResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SpendingRhythmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/rhythm?weeks=13
 * -> the days of the week and the parts of the month when the user spends, and the days without
 * purchase, over the last weeks (4 to 52).
 */
@RestController
@RequestMapping("/api/analytics/rhythm")
@RequiredArgsConstructor
public class SpendingRhythmController {

    private final SpendingRhythmService spendingRhythmService;

    @GetMapping
    public ResponseEntity<SpendingRhythmResponse> analyse(
            @RequestParam(name = "weeks", defaultValue = "13") int weeks,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingRhythmService.analyse(user, weeks));
    }
}
