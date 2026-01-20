package com.gocommerce.auth.config;

import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    static {
        // Set a timezone that Postgres accepts
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        // or: TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }
}
