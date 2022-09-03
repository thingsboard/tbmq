package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingCtx;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionTopicFilter;

@Data
@RequiredArgsConstructor
public class ApplicationSharedSubscriptionCtx {

    private final SharedSubscriptionTopicFilter subscription;
    private final ApplicationPackProcessingCtx packProcessingCtx;
}
