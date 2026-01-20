package com.gocommerce.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import java.util.TimeZone;
@EnableKafka
@SpringBootApplication
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
