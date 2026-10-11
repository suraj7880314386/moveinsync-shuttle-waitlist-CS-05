package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final ShuttleMetrics metrics;
    private final Clock clock;

    public BackupService(JdbcTemplate jdbc, AppProperties props, ShuttleMetrics metrics, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.metrics = metrics;
        this.clock = clock;
    }

    public String backup() {
        try {
            Path dir = Paths.get(props.backup().directory()).toAbsolutePath();
            Files.createDirectories(dir);
            Path target = dir.resolve("shuttle-" + TS.format(clock.instant()) + ".zip");
            String path = target.toString().replace("\\", "/").replace("'", "''");
            jdbc.execute("BACKUP TO '" + path + "'");
            metrics.backup("success");
            log.info("event=backup_done file={}", target);
            return target.toString();
        } catch (IOException | RuntimeException ex) {
            metrics.backup("failure");
            log.error("event=backup_failed error={}", ex.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_FAILED", "Backup could not be created");
        }
    }
}
