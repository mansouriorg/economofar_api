package com.economofar.api.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_notifications")
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(length = 3000)
    private String metadata;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AppNotification() {
    }

    public AppNotification(User user, NotificationType type, String title, String body, String metadata) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.body = body;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getMetadata() {
        return metadata;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}