package com.moveinsync.shuttle.notification;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.domain.AppUser;
import com.moveinsync.shuttle.domain.Notification;
import com.moveinsync.shuttle.domain.NotificationStatus;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import com.moveinsync.shuttle.repository.NotificationRepository;
import com.moveinsync.shuttle.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final SmsGateway gateway;
    private final ShuttleMetrics metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int maxAttempts;

    public NotificationService(NotificationRepository notifications, UserRepository users, SmsGateway gateway,
                               ShuttleMetrics metrics, ApplicationEventPublisher events, Clock clock,
                               AppProperties props) {
        this.notifications = notifications;
        this.users = users;
        this.gateway = gateway;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
        this.maxAttempts = props.notifications().maxAttempts();
    }

    @Transactional
    public void enqueue(Long userId, String message) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            log.warn("event=notification_skipped reason=user_missing userId={}", userId);
            return;
        }
        notifications.save(new Notification(userId, user.getPhone(), message, clock.instant()));
        events.publishEvent(new NotificationQueuedEvent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchPending() {
        List<Notification> batch = notifications.findTop50ByStatusOrderByIdAsc(NotificationStatus.PENDING);
        int sent = 0;
        for (Notification n : batch) {
            try {
                gateway.send(n.getPhone(), n.getMessage());
                n.markSent(clock.instant());
                metrics.notificationSent();
                sent++;
            } catch (Exception ex) {
                n.markAttemptFailed(ex.getMessage(), maxAttempts);
                metrics.notificationFailed();
                log.warn("event=sms_failed notificationId={} attempts={} status={} error={}",
                        n.getId(), n.getAttempts(), n.getStatus(), ex.getMessage());
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<Notification> recent() {
        return notifications.findTop100ByOrderByIdDesc();
    }
}
