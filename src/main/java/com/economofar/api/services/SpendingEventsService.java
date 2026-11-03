package com.economofar.api.services;

import com.economofar.api.dtos.SpendingEventCandidatesResponse;
import com.economofar.api.dtos.SpendingEventDeleteResponse;
import com.economofar.api.dtos.SpendingEventDetailResponse;
import com.economofar.api.dtos.SpendingEventRequest;
import com.economofar.api.dtos.SpendingEventResponse;
import com.economofar.api.dtos.SpendingEventTicketRemoveRequest;
import com.economofar.api.dtos.SpendingEventTicketsRequest;
import com.economofar.api.dtos.SpendingEventsResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.SpendingEvent;
import com.economofar.api.models.SpendingEventTicket;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.SpendingEventRepository;
import com.economofar.api.repositories.SpendingEventTicketRepository;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the events of the person and the tickets they hold, and reads the saved tickets to tell where
 * the spending of each event stands (SpendingEventsCalculator does the figures, SpendingEventRules
 * decides what an event may look like). A ticket is in one event at most; a ticket that was deleted, or
 * is not saved any more, is left out of the figures. Putting a ticket in an event or taking it out never
 * changes the ticket.
 */
@Service
@RequiredArgsConstructor
public class SpendingEventsService {

    static final int MAX_EVENTS = 30;
    static final int MAX_TICKETS_PER_EVENT = 500;
    static final int MAX_TICKETS_PER_CALL = 50;
    static final int MAX_QUERY_LENGTH = 100;
    static final int CHUNK = 400;

    private final TicketRepository ticketRepository;
    private final SpendingEventRepository eventRepository;
    private final SpendingEventTicketRepository linkRepository;

    // ---- reading

    @Transactional(readOnly = true)
    public SpendingEventsResponse overview(User user) {
        return overview(user, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the dates of an event can be tested. */
    public SpendingEventsResponse overview(User user, LocalDate today) {
        requireUser(user);

        List<SpendingEvent> events = eventRepository.findByUser(user);
        List<SpendingEventTicket> links = linkRepository.findByUser(user);

        Map<UUID, Ticket> tickets = loadTickets(user, ticketIds(links));
        Map<Long, List<SpendingEventsCalculator.Spend>> spends = new HashMap<>();

        for (SpendingEventTicket link : links) {
            Ticket ticket = tickets.get(link.getTicketId());

            if (ticket != null) {
                spends.computeIfAbsent(link.getEvent().getId(), key -> new ArrayList<>()).add(spendOf(ticket));
            }
        }

        List<SpendingEventsCalculator.Info> infos = new ArrayList<>();

        for (SpendingEvent event : events) {
            infos.add(infoOf(event));
        }

        return SpendingEventsCalculator.overview(infos, spends, today);
    }

    @Transactional(readOnly = true)
    public SpendingEventDetailResponse detail(User user, Long id) {
        return detail(user, id, LocalDate.now());
    }

    public SpendingEventDetailResponse detail(User user, Long id, LocalDate today) {
        requireUser(user);

        return detailOf(user, findEvent(user, id), today);
    }

    /** The saved tickets that are in no event, to choose the ones to put in this event. */
    @Transactional(readOnly = true)
    public SpendingEventCandidatesResponse candidates(User user, Long id, String query) {
        return candidates(user, id, query, LocalDate.now());
    }

    public SpendingEventCandidatesResponse candidates(User user, Long id, String query, LocalDate today) {
        requireUser(user);

        String text = SpendingEventRules.cleanName(query);

        if (text.codePointCount(0, text.length()) > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The search is too long: " + MAX_QUERY_LENGTH + " characters at most");
        }

        SpendingEvent event = findEvent(user, id);
        SpendingEventsCalculator.Info info = infoOf(event);
        SpendingEventsCalculator.Range range = SpendingEventsCalculator.candidateRange(info, text, today);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                range.from().atStartOfDay(),
                range.to().atTime(LocalTime.MAX)
        );

        Set<UUID> taken = new HashSet<>(ticketIds(linkRepository.findByUser(user)));
        List<SpendingEventsCalculator.Spend> pool = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (usable(user, ticket) && !taken.contains(ticket.getId())) {
                pool.add(spendOf(ticket));
            }
        }

        return SpendingEventsCalculator.candidates(info, pool, text, today);
    }

    // ---- writing

    @Transactional
    public SpendingEventResponse create(User user, SpendingEventRequest request) {
        return create(user, request, LocalDate.now());
    }

    public SpendingEventResponse create(User user, SpendingEventRequest request, LocalDate today) {
        requireUser(user);

        SpendingEventRules.Checked checked = check(request);
        List<SpendingEvent> own = eventRepository.findByUser(user);

        if (own.size() >= MAX_EVENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many events: " + MAX_EVENTS + " at most");
        }

        requireFreeName(own, checked.nameKey(), null);

        SpendingEvent event = new SpendingEvent(
                user,
                checked.name(),
                checked.nameKey(),
                checked.icon(),
                checked.budget(),
                checked.startDate(),
                checked.endDate()
        );

        try {
            event = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // The same name was given to an event by another call at the same moment
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An event with this name already exists");
        }

        return SpendingEventsCalculator.summarise(infoOf(event), List.of(), today);
    }

    @Transactional
    public SpendingEventResponse update(User user, Long id, SpendingEventRequest request) {
        return update(user, id, request, LocalDate.now());
    }

    public SpendingEventResponse update(User user, Long id, SpendingEventRequest request, LocalDate today) {
        requireUser(user);

        SpendingEvent event = findEvent(user, id);
        SpendingEventRules.Checked checked = check(request);

        requireFreeName(eventRepository.findByUser(user), checked.nameKey(), event.getId());

        event.setName(checked.name());
        event.setNameKey(checked.nameKey());
        event.setIcon(checked.icon());
        event.setBudget(checked.budget());
        event.setStartDate(checked.startDate());
        event.setEndDate(checked.endDate());

        if (request.archived() != null) {
            event.setArchived(request.archived());
        }

        try {
            event = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An event with this name already exists");
        }

        return SpendingEventsCalculator.summarise(infoOf(event), spendsOf(user, event), today);
    }

    /** Deletes the event and its links, not the tickets. releasedTickets counts the tickets that are still saved. */
    @Transactional
    public SpendingEventDeleteResponse delete(User user, Long id) {
        requireUser(user);

        SpendingEvent event = findEvent(user, id);
        int released = spendsOf(user, event).size();

        try {
            linkRepository.deleteByEvent(event);
            eventRepository.delete(event);
            eventRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // A ticket was put in the event by another call at the same moment
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A ticket was put in the event at the same moment, try again");
        }

        return new SpendingEventDeleteResponse(event.getId(), released);
    }

    /** Puts tickets in the event. A ticket that is already in it stays; one that is in another event is refused. The 500 of the limit are the tickets that are still saved. */
    @Transactional
    public SpendingEventDetailResponse attach(User user, Long id, SpendingEventTicketsRequest request) {
        return attach(user, id, request, LocalDate.now());
    }

    public SpendingEventDetailResponse attach(User user, Long id, SpendingEventTicketsRequest request, LocalDate today) {
        requireUser(user);

        SpendingEvent event = findEvent(user, id);
        List<String> raw = request == null ? null : request.ticketIds();

        if (raw == null || raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one ticket is required");
        }

        if (raw.size() > MAX_TICKETS_PER_CALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many tickets at once: " + MAX_TICKETS_PER_CALL + " at most");
        }

        Set<UUID> wanted = new LinkedHashSet<>();

        for (String text : raw) {
            wanted.add(parseTicketId(text));
        }

        Map<UUID, Ticket> tickets = loadTickets(user, wanted);

        for (UUID ticketId : wanted) {
            if (!tickets.containsKey(ticketId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A ticket was not found");
            }
        }

        Map<UUID, SpendingEventTicket> existing = new HashMap<>();

        for (SpendingEventTicket link : linkRepository.findByUserAndTicketIdIn(user, wanted)) {
            existing.put(link.getTicketId(), link);
        }

        List<UUID> fresh = new ArrayList<>();

        for (UUID ticketId : wanted) {
            SpendingEventTicket link = existing.get(ticketId);

            if (link == null) {
                fresh.add(ticketId);
            } else if (!Objects.equals(link.getEvent().getId(), event.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A ticket is already in another event");
            }
        }

        if (!fresh.isEmpty()) {
            if (spendsOf(user, event).size() + fresh.size() > MAX_TICKETS_PER_EVENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An event holds " + MAX_TICKETS_PER_EVENT + " tickets at most");
            }

            try {
                for (UUID ticketId : fresh) {
                    linkRepository.save(new SpendingEventTicket(event, user, ticketId));
                }

                linkRepository.flush();
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A ticket was put in an event at the same moment, try again");
            }
        }

        return detailOf(user, event, today);
    }

    /** Takes a ticket out of the event. */
    @Transactional
    public SpendingEventDetailResponse detach(User user, Long id, SpendingEventTicketRemoveRequest request) {
        return detach(user, id, request, LocalDate.now());
    }

    public SpendingEventDetailResponse detach(User user, Long id, SpendingEventTicketRemoveRequest request, LocalDate today) {
        requireUser(user);

        SpendingEvent event = findEvent(user, id);
        UUID ticketId = parseTicketId(request == null ? null : request.ticketId());

        SpendingEventTicket link = linkRepository.findByEventAndTicketId(event, ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This ticket is not in the event"));

        linkRepository.delete(link);

        return detailOf(user, event, today);
    }

    // ---- reading helpers

    private SpendingEventDetailResponse detailOf(User user, SpendingEvent event, LocalDate today) {
        return SpendingEventsCalculator.detail(infoOf(event), spendsOf(user, event), today);
    }

    private List<SpendingEventsCalculator.Spend> spendsOf(User user, SpendingEvent event) {
        Map<UUID, Ticket> tickets = loadTickets(user, ticketIds(linkRepository.findByEvent(event)));
        List<SpendingEventsCalculator.Spend> spends = new ArrayList<>();

        for (Ticket ticket : tickets.values()) {
            spends.add(spendOf(ticket));
        }

        return spends;
    }

    /** The saved tickets of the person among the ids, by id. The ids are looked up a few hundred at a time. */
    private Map<UUID, Ticket> loadTickets(User user, Collection<UUID> ids) {
        Map<UUID, Ticket> found = new HashMap<>();
        List<UUID> all = new ArrayList<>(new LinkedHashSet<>(ids));

        for (int from = 0; from < all.size(); from += CHUNK) {
            List<UUID> part = all.subList(from, Math.min(from + CHUNK, all.size()));

            for (Ticket ticket : ticketRepository.findAllById(part)) {
                if (usable(user, ticket)) {
                    found.put(ticket.getId(), ticket);
                }
            }
        }

        return found;
    }

    /** A ticket that counts: saved, with a date, and the person's own. */
    private boolean usable(User user, Ticket ticket) {
        return ticket != null
                && ticket.getId() != null
                && ticket.getScanStatus() == ScanStatus.SAVED
                && ticket.getPurchaseDate() != null
                && ticket.getUser() != null
                && Objects.equals(ticket.getUser().getId(), user.getId());
    }

    private List<UUID> ticketIds(List<SpendingEventTicket> links) {
        List<UUID> ids = new ArrayList<>();

        for (SpendingEventTicket link : links) {
            ids.add(link.getTicketId());
        }

        return ids;
    }

    private SpendingEventsCalculator.Spend spendOf(Ticket ticket) {
        return new SpendingEventsCalculator.Spend(
                ticket.getId().toString(),
                ticket.getMerchantName(),
                ticket.getCategory(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate().toLocalDate()
        );
    }

    private SpendingEventsCalculator.Info infoOf(SpendingEvent event) {
        return new SpendingEventsCalculator.Info(
                event.getId(),
                event.getName(),
                event.getIcon(),
                event.getBudget(),
                event.getStartDate(),
                event.getEndDate(),
                event.isArchived(),
                event.getCreatedAt()
        );
    }

    // ---- checks

    private SpendingEvent findEvent(User user, Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        return eventRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private SpendingEventRules.Checked check(SpendingEventRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A name is required");
        }

        try {
            return SpendingEventRules.check(request.name(), request.icon(), request.budget(), request.startDate(), request.endDate());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void requireFreeName(List<SpendingEvent> own, String nameKey, Long exceptId) {
        for (SpendingEvent other : own) {
            if (nameKey.equals(other.getNameKey()) && !Objects.equals(other.getId(), exceptId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An event with this name already exists");
            }
        }
    }

    private UUID parseTicketId(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ticket id is required");
        }

        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This ticket id is not valid");
        }
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }
}
