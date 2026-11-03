package com.economofar.api.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An event of the person (a trip, a renovation, a wedding...) that groups saved tickets, with a budget
 * and dates that are both optional. nameKey is the name without capitals, accents or extra spaces: two
 * events of the same person can not share it.
 */
@Entity
@Table(
        name = "spending_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_spending_events_user_name", columnNames = {"user_id", "name_key"})
)
public class SpendingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "name_key", nullable = false, length = 240)
    private String nameKey;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon;

    @Column(name = "budget", precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SpendingEvent() {
    }

    public SpendingEvent(User user, String name, String nameKey, String icon, BigDecimal budget, LocalDate startDate, LocalDate endDate) {
        this.user = user;
        this.name = name;
        this.nameKey = nameKey;
        this.icon = icon;
        this.budget = budget;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
