package com.moveinsync.shuttle.metrics;

import com.moveinsync.shuttle.domain.BookingStatus;
import com.moveinsync.shuttle.repository.BookingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
@Component
public class ShuttleMetrics {
    private final MeterRegistry registry;
    private final Counter confirmed;
    private final Counter waitlisted;
    private final Counter promoted;
    private final Counter noShow;
    private final Counter notificationSent;
    private final Counter notificationFailed;
    private final Counter invariantViolation;
    private final Timer passTimer;

    public ShuttleMetrics(MeterRegistry registry, BookingRepository bookings) {
        this.registry = registry;
        this.confirmed = Counter.builder("shuttle.booking.confirmed").description("Bookings confirmed immediately").register(registry);
        this.waitlisted = Counter.builder("shuttle.booking.waitlisted").description("Bookings placed on waitlist").register(registry);
        this.promoted = Counter.builder("shuttle.promotion.total").description("Waitlist -> confirmed promotions").register(registry);
        this.noShow = Counter.builder("shuttle.noshow.reclaimed").description("Seats reclaimed from no-shows").register(registry);
        this.notificationSent = Counter.builder("shuttle.notification.sent").register(registry);
        this.notificationFailed = Counter.builder("shuttle.notification.failed").register(registry);
        this.invariantViolation = Counter.builder("shuttle.invariant.violation").description("Capacity/quota invariant breaches (must stay 0)").register(registry);
        this.passTimer = Timer.builder("shuttle.promotion.pass.duration").description("Duration of one promotion pass").register(registry);
        Gauge.builder("shuttle.waitlist.size", bookings, r -> r.countByStatus(BookingStatus.WAITLISTED))
                .description("Currently waitlisted bookings").register(registry);
    }

    public void bookingConfirmed() { confirmed.increment(); }
    public void bookingWaitlisted() { waitlisted.increment(); }
    public void bookingCancelled(String reason) { registry.counter("shuttle.booking.cancelled", "reason", reason).increment(); }
    public void promoted(int n) { if (n > 0) promoted.increment(n); }
    public void promotionSkipped(String reason, int n) { if (n > 0) registry.counter("shuttle.promotion.skipped", "reason", reason).increment(n); }
    public void noShowReclaimed(int n) { if (n > 0) noShow.increment(n); }
    public void rejected(String code) { registry.counter("shuttle.request.rejected", "code", code).increment(); }
    public void notificationSent() { notificationSent.increment(); }
    public void notificationFailed() { notificationFailed.increment(); }
    public void invariantViolation() { invariantViolation.increment(); }
    public void backup(String outcome) { registry.counter("shuttle.backup", "outcome", outcome).increment(); }

    public <T> T timePass(Supplier<T> work) { return passTimer.record(work); }
}
