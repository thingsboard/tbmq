package org.thingsboard.mqtt.broker.service.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;

@Component("kafka")
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    private final TbQueueAdmin kafkaAdmin;

    @Override
    public Health health() {
        try {
            return Health.up().withDetail("brokerCount", kafkaAdmin.getNodes().size()).build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
