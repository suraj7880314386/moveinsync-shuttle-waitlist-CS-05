package com.moveinsync.shuttle.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TripCacheEvictor {
    private final CacheManager cacheManager;

    public TripCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripChanged(TripChangedEvent event) {
        Cache cache = cacheManager.getCache(AvailabilityService.CACHE);
        if (cache != null) {
            cache.evict(event.tripId());
        }
    }
}
