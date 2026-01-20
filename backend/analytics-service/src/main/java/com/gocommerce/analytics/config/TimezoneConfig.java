package com.gocommerce.analytics.config;

import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    static {
        // Set default JVM timezone to UTC for this service
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
