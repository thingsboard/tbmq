/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceSessionClearerImpl implements PersistenceSessionClearer {
    private final ClientSubscriptionService clientSubscriptionService;
    private final MsgPersistenceManager msgPersistenceManager;

    @Override
    public void clearPersistedSession(ClientInfo clientInfo) {
        log.debug("[{}][{}] Clearing persisted session.", clientInfo.getType(), clientInfo.getClientId());
        clientSubscriptionService.clearSubscriptions(clientInfo.getClientId());
        msgPersistenceManager.clearPersistedMessages(clientInfo);
    }
}
