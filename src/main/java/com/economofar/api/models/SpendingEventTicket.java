package com.economofar.api.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The link between an event and a ticket. A ticket belongs to one event at most. ticketId is the id of
 * the ticket and not a foreign key: deleting a ticket never fails because of an event, and a link whose
 * ticket is gone is ignored.
 */
@Entity
@Table(
        name = "spending_event_tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_spending_event_tickets_ticket", columnNames = {"ticket_id"}),
        indexes = @Index(name = "idx_spending_event_tickets_event", columnList = "event_id")
)
public class SpendingEventTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private SpendingEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    public SpendingEventTicket() {
    }

    public SpendingEventTicket(SpendingEvent event, User user, UUID ticketId) {
        this.event = event;
        this.user = user;
        this.ticketId = ticketId;
    }

    public Long getId() {
        return id;
    }

    public SpendingEvent getEvent() {
        return event;
    }

    public void setEvent(SpendingEvent event) {
        this.event = event;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
