package com.moveinsync.shuttle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShuttleWaitlistApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShuttleWaitlistApplication.class, args);
    }
}
