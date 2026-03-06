/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.entity.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbWebSocketSubscriptionService extends AbstractTbEntityService implements TbWebSocketSubscriptionService {

    private final WebSocketSubscriptionService webSocketSubscriptionService;

    @Override
    public WebSocketSubscription save(WebSocketSubscription subscription, User currentUser) {
        return webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Override
    public void delete(WebSocketSubscription subscription, User currentUser) {
        webSocketSubscriptionService.deleteWebSocketSubscription(subscription.getId());
    }

}
