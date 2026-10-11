package com.moveinsync.shuttle.notification;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService service;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sms-dispatcher");
        t.setDaemon(true);
        return t;
    });

    public NotificationListener(NotificationService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationQueuedEvent event) {
        executor.execute(this::dispatch);
    }

    @Scheduled(initialDelayString = "${app.notifications.retry-interval-ms:15000}",
            fixedDelayString = "${app.notifications.retry-interval-ms:15000}")
    public void retry() {
        dispatch();
    }

    void dispatch() {
        lock.lock(); 
        try {
            service.dispatchPending();
        } catch (Exception ex) {
            log.error("event=dispatch_error error={}", ex.toString());
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
