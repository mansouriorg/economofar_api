package com.economofar.api.controllers;

import com.economofar.api.dtos.SpendingEventCandidatesResponse;
import com.economofar.api.dtos.SpendingEventDeleteResponse;
import com.economofar.api.dtos.SpendingEventDetailResponse;
import com.economofar.api.dtos.SpendingEventRequest;
import com.economofar.api.dtos.SpendingEventResponse;
import com.economofar.api.dtos.SpendingEventTicketRemoveRequest;
import com.economofar.api.dtos.SpendingEventTicketsRequest;
import com.economofar.api.dtos.SpendingEventsResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SpendingEventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The events of the person: a trip, a renovation, a wedding... with the saved tickets they hold.
 *
 * GET  /api/events                          -> the events with their spending, the ones in progress first
 * GET  /api/events/{id}                     -> one event with its categories, shops, days and tickets
 * GET  /api/events/{id}/candidates?query=   -> the saved tickets that are in no event, to put in this one
 * POST /api/events                          -> creates an event: name, icon, budget, startDate, endDate
 * POST /api/events/{id}/update              -> changes an event (same fields, and archived)
 * POST /api/events/{id}/delete              -> deletes an event, not its tickets
 * POST /api/events/{id}/tickets             -> puts tickets in an event: { "ticketIds": [...] }
 * POST /api/events/{id}/tickets/remove      -> takes a ticket out of an event: { "ticketId": "..." }
 *
 * Only GET and POST are used, so that every client can call them. A field that is not acceptable gives a
 * 400 with the reason, a name that is taken or a ticket that is in another event a 409, an event or a
 * ticket that is not found a 404.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SpendingEventsController {

    private final SpendingEventsService spendingEventsService;

    @GetMapping
    public ResponseEntity<SpendingEventsResponse> overview(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(spendingEventsService.overview(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpendingEventDetailResponse> detail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.detail(user, id));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<SpendingEventCandidatesResponse> candidates(
            @PathVariable("id") Long id,
            @RequestParam(name = "query", required = false) String query,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.candidates(user, id, query));
    }

    @PostMapping
    public ResponseEntity<SpendingEventResponse> create(
            @RequestBody SpendingEventRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.create(user, request));
    }

    @PostMapping("/{id}/update")
    public ResponseEntity<SpendingEventResponse> update(
            @PathVariable("id") Long id,
            @RequestBody SpendingEventRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.update(user, id, request));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<SpendingEventDeleteResponse> delete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.delete(user, id));
    }

    @PostMapping("/{id}/tickets")
    public ResponseEntity<SpendingEventDetailResponse> attach(
            @PathVariable("id") Long id,
            @RequestBody SpendingEventTicketsRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.attach(user, id, request));
    }

    @PostMapping("/{id}/tickets/remove")
    public ResponseEntity<SpendingEventDetailResponse> detach(
            @PathVariable("id") Long id,
            @RequestBody SpendingEventTicketRemoveRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingEventsService.detach(user, id, request));
    }
}
