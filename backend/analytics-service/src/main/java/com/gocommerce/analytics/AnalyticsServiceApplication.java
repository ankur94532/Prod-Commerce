package com.gocommerce.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import java.util.TimeZone;
@EnableKafka
@SpringBootApplication
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
