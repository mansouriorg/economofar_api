package com.economofar.api.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Where a category of the person goes in the split of the spending into needs, wants and savings:
 * "NEED", "WANT" or "EXCLUDED" (left out of the calculation). A category without rule goes where the
 * app proposes. categoryKey is the name of the category without the spaces around it and without
 * capitals, so that every spelling of a category shares one rule.
 */
@Entity
@Table(
        name = "spending_split_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_spending_split_rules_user_category", columnNames = {"user_id", "category_key"})
)
public class SpendingSplitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category_key", nullable = false, length = 120)
    private String categoryKey;

    @Column(name = "bucket", nullable = false, length = 12)
    private String bucket;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SpendingSplitRule() {
    }

    public SpendingSplitRule(User user, String categoryKey, String bucket) {
        this.user = user;
        this.categoryKey = categoryKey;
        this.bucket = bucket;
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

    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
