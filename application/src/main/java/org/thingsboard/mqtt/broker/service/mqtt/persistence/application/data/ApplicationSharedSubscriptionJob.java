package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionTopicFilter;

import java.util.concurrent.Future;

@Data
@AllArgsConstructor
public class ApplicationSharedSubscriptionJob {

    private final SharedSubscriptionTopicFilter subscription;
    private volatile Future<?> future;
    private volatile boolean interrupted;

    public boolean interrupted() {
        return this.isInterrupted();
    }
}
