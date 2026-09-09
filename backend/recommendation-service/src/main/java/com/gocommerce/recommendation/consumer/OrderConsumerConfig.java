package com.gocommerce.recommendation.consumer;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

/**
 * A poison record used to block its partition forever, with unlimited retries and no
 * alerting: safe for correctness, but an unbounded outage for every later order on that
 * partition. It is now retried with backoff and then parked on {@code <topic>.DLT}, which
 * is durable and observable. Parked events are not processed events: alert on DLT depth
 * and replay them once repaired. Inbox deduplication makes that replay safe.
 */
@Configuration
public class OrderConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerConfig.class);

    @Bean
    public DefaultErrorHandler orderProjectionErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${gocommerce.consumer.retry-attempts:5}") int attempts,
            @Value("${gocommerce.consumer.retry-initial-interval:1s}") Duration initialInterval,
            @Value("${gocommerce.consumer.retry-multiplier:2.0}") double multiplier,
            @Value("${gocommerce.consumer.retry-max-interval:30s}") Duration maxInterval) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> {
                    log.error("Parking order event on the dead-letter topic after {} attempts: "
                            + "topic={} partition={} offset={}",
                            attempts, record.topic(), record.partition(), record.offset(), exception);
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval.toMillis(), multiplier);
        backOff.setMaxInterval(maxInterval.toMillis());
        backOff.setMaxAttempts(attempts);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setCommitRecovered(true);
        return handler;
    }
}
