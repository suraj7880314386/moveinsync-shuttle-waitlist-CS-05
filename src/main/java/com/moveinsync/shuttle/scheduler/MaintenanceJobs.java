package com.moveinsync.shuttle.scheduler;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.service.BackupService;
import com.moveinsync.shuttle.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final ReconciliationService reconciliation;
    private final BackupService backups;
    private final AppProperties props;

    public MaintenanceJobs(ReconciliationService reconciliation, BackupService backups, AppProperties props) {
        this.reconciliation = reconciliation;
        this.backups = backups;
        this.props = props;
    }

    @Scheduled(initialDelay = 5000, fixedDelayString = "${app.reconcile.interval-ms}")
    public void reconcile() {
        reconciliation.reconcileAll();
    }

    @Scheduled(initialDelayString = "${app.backup.interval-ms}", fixedDelayString = "${app.backup.interval-ms}")
    public void backup() {
        if (!props.backup().enabled()) {
            return;
        }
        try {
            backups.backup();
        } catch (Exception ex) {
            log.error("event=scheduled_backup_failed error={}", ex.toString());
        }
    }
}
