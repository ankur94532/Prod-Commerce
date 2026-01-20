package com.gocommerce.analytics.config;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneConfigTest {

    @Test
    void staticBlock_setsDefaultTimezoneToUtc() throws Exception {
        // Force class loading so static initializer runs
        Class.forName("com.gocommerce.analytics.config.TimezoneConfig");

        TimeZone defaultTz = TimeZone.getDefault();
        assertThat(defaultTz.getID()).isEqualTo("UTC");
    }
}
