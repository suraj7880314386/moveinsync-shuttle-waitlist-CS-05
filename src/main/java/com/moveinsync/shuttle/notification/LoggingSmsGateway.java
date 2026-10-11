package com.moveinsync.shuttle.notification;

import com.moveinsync.shuttle.config.AppProperties;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingSmsGateway implements SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);
    private final double failureProbability;

    public LoggingSmsGateway(AppProperties props) {
        this.failureProbability = props.sms() == null ? 0.0 : props.sms().failureProbability();
    }

    @Override
    public void send(String phone, String message) {
        if (failureProbability > 0 && ThreadLocalRandom.current().nextDouble() < failureProbability) {
            throw new IllegalStateException("Simulated SMS provider outage");
        }
        log.info("event=sms_sent to={} message=\"{}\"", mask(phone), message);
    }

    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
