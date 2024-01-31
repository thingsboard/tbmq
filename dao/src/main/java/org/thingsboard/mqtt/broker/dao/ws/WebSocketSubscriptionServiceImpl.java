/**
 * Copyright © 2016-2023 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.dao.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validateId;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketSubscriptionServiceImpl implements WebSocketSubscriptionService {

    private final WebSocketSubscriptionDao webSocketSubscriptionDao;
    private final WebSocketConnectionDao webSocketConnectionDao;

    @Override
    public WebSocketSubscription saveWebSocketSubscription(WebSocketSubscription subscription) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveWebSocketSubscription [{}]", subscription);
        }
        webSocketSubscriptionValidator.validate(subscription);
        return webSocketSubscriptionDao.save(subscription);
    }

    @Override
    public WebSocketSubscription saveDefaultWebSocketSubscription(UUID webSocketConnectionId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveDefaultWebSocketSubscription [{}]", webSocketConnectionId);
        }
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(webSocketConnectionId);
        subscription.setConfiguration(getWebSocketSubscriptionConfiguration());
        return saveWebSocketSubscription(subscription);
    }

    @Override
    public List<WebSocketSubscription> getWebSocketSubscriptions(UUID webSocketConnectionId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getWebSocketSubscriptions, webSocketConnectionId [{}]", webSocketConnectionId);
        }
        validateId(webSocketConnectionId, "Incorrect webSocketConnectionId " + webSocketConnectionId);
        return webSocketSubscriptionDao.findAllByWebSocketConnectionId(webSocketConnectionId);
    }

    @Override
    public Optional<WebSocketSubscription> getWebSocketSubscriptionById(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getWebSocketSubscriptionById [{}]", id);
        }
        return Optional.ofNullable(webSocketSubscriptionDao.findById(id));
    }

    @Override
    public boolean deleteWebSocketSubscription(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteWebSocketSubscription [{}]", id);
        }
        return webSocketSubscriptionDao.removeById(id);
    }

    private final DataValidator<WebSocketSubscription> webSocketSubscriptionValidator =
            new DataValidator<>() {

                @Override
                protected void validateUpdate(WebSocketSubscription webSocketSubscription) {
                    WebSocketSubscription existingSubscription = webSocketSubscriptionDao.findById(webSocketSubscription.getId());
                    if (existingSubscription == null) {
                        throw new DataValidationException("Unable to update non-existent WebSocket Subscription!");
                    }
                }

                @Override
                protected void validateDataImpl(WebSocketSubscription webSocketSubscription) {
                    if (webSocketSubscription.getWebSocketConnectionId() == null) {
                        throw new DataValidationException("WebSocket Subscription should be assigned to WebSocket connection!");
                    } else {
                        if (webSocketConnectionDao.findById(webSocketSubscription.getWebSocketConnectionId()) == null) {
                            throw new DataValidationException("WebSocket Subscription is referencing to non-existent WebSocket connection!");
                        }
                    }
                    if (webSocketSubscription.getConfiguration() == null) {
                        throw new DataValidationException("WebSocket Subscription configuration should be specified!");
                    }
                    validateString("WebSocket Subscription topicFilter", webSocketSubscription.getConfiguration().getTopicFilter());
                }
            };

    private WebSocketSubscriptionConfiguration getWebSocketSubscriptionConfiguration() {
        return new WebSocketSubscriptionConfiguration(
                BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_TOPIC_FILTER,
                BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_QOS,
                BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_COLOR);
    }

}
