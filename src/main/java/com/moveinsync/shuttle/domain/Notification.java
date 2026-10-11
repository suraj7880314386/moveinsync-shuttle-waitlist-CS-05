package com.moveinsync.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_status", columnList = "status"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(length = 300)
    private String lastError;

    protected Notification() {}

    public Notification(Long userId, String phone, String message, Instant createdAt) {
        this.userId = userId;
        this.phone = phone;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
        this.createdAt = createdAt;
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.attempts++;
        this.lastError = null;
    }

    public void markAttemptFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? "unknown" : (error.length() > 300 ? error.substring(0, 300) : error);
        if (this.attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
}
