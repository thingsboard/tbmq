/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientLoggerImpl implements ClientLogger {
    @Value("${analysis.log.analyzed-client-ids:}")
    private Set<String> analyzedClientIds;

    @Override
    public void logEvent(String clientId, Class<?> eventLocation, String eventDescription) {
        if (!log.isDebugEnabled() || CollectionUtils.isEmpty(analyzedClientIds) || !analyzedClientIds.contains(clientId)) {
            return;
        }
        log.debug("[{}][{}] {}", clientId, eventLocation.getSimpleName(), eventDescription);
    }
}
