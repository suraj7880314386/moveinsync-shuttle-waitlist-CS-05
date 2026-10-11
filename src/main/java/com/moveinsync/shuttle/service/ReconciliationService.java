package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.Trip;
import com.moveinsync.shuttle.domain.SeatLedger;
import com.moveinsync.shuttle.dto.Views.ReconcileResult;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import com.moveinsync.shuttle.repository.TripRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TripRepository trips;
    private final WaitlistPromotionService promotion;
    private final SeatLedgerLoader ledgerLoader;
    private final ShuttleMetrics metrics;
    private final TransactionTemplate tx;

    public ReconciliationService(TripRepository trips, WaitlistPromotionService promotion, SeatLedgerLoader ledgerLoader,
                                 ShuttleMetrics metrics, PlatformTransactionManager txManager) {
        this.trips = trips;
        this.promotion = promotion;
        this.ledgerLoader = ledgerLoader;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(txManager);
    }

    public ReconcileResult reconcileAll() {
        int checked = 0;
        int promotions = 0;
        int violations = 0;
        for (Long tripId : trips.findAllIds()) {
            try {
                int[] r = tx.execute(status -> reconcileOne(tripId));
                checked++;
                promotions += r[0];
                violations += r[1];
            } catch (Exception ex) {
                log.error("event=reconcile_failed tripId={} error={}", tripId, ex.toString());
            }
        }
        log.info("event=reconcile_done trips={} promotions={} violations={}", checked, promotions, violations);
        return new ReconcileResult(checked, promotions, violations);
    }

    private int[] reconcileOne(Long tripId) {
        Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
        if (trip == null) {
            return new int[]{0, 0};
        }
        int promoted = promotion.runPass(trip, null).size();
        SeatLedger ledger = ledgerLoader.load(trip);
        int violations = 0;
        if (ledger.confirmedSeats() > ledger.capacity()) {
            violations++;
            log.error("event=invariant_violation type=capacity tripId={} confirmed={} capacity={}",
                    tripId, ledger.confirmedSeats(), ledger.capacity());
        }
        for (Map.Entry<String, Integer> q : ledger.quotas().entrySet()) {
            if (ledger.used(q.getKey()) > q.getValue()) {
                violations++;
                log.error("event=invariant_violation type=quota tripId={} costCentre={} used={} quota={}",
                        tripId, q.getKey(), ledger.used(q.getKey()), q.getValue());
            }
        }
        for (int i = 0; i < violations; i++) {
            metrics.invariantViolation();
        }
        return new int[]{promoted, violations};
    }
}
