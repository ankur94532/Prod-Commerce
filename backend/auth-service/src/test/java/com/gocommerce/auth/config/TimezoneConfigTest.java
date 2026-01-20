package com.gocommerce.auth.config;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneConfigTest {

    @Test
    void defaultTimeZone_isUtc() {
        // Force class load (static block runs once)
        new TimezoneConfig();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
