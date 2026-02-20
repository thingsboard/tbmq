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
package org.thingsboard.mqtt.broker.service.entity.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionAdminService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbSubscriptionService extends AbstractTbEntityService implements TbSubscriptionService {

    private final SubscriptionService subscriptionService;
    private final ClientSubscriptionAdminService clientSubscriptionAdminService;

    @Override
    public void updateSubscriptions(String clientId, List<SubscriptionInfoDto> subscriptions, User currentUser) throws ThingsboardException {
        clientSubscriptionAdminService.updateSubscriptions(clientId, subscriptions);
    }

    @Override
    public void clearEmptyNodes(User currentUser) {
        try {
            subscriptionService.clearEmptyTopicNodes();
        } catch (SubscriptionTrieClearException e) {
            throw new ThingsboardRuntimeException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

}
