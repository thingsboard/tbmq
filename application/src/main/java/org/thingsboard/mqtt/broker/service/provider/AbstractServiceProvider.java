/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractServiceProvider {

    private final TbQueueAdmin tbQueueAdmin;
    private final ServiceInfoProvider serviceInfoProvider;

    protected boolean isCurrentNodeShouldCleanUp() {
        return isThisNodeResponsibleForTask(getServiceId(), "ts cleanup");
    }

    protected boolean isCurrentNodeShouldCheckAvailableVersion() {
        return isThisNodeResponsibleForTask(getServiceId(), "check latest available version");
    }

    private String getServiceId() {
        return serviceInfoProvider.getServiceId();
    }

    private boolean isThisNodeResponsibleForTask(String serviceId, String logMsg) {
        List<String> brokerServiceIds = tbQueueAdmin.getBrokerServiceIds().stream().sorted().toList();
        if (!CollectionUtils.isEmpty(brokerServiceIds)) {
            String firstServiceId = brokerServiceIds.get(0);
            return serviceId.equals(firstServiceId);
        }
        log.info("[{}] Could not find all service ids, proceeding with {} task!", serviceId, logMsg);
        return true;
    }

    protected boolean isCurrentNodeShouldCleanUpEvents() {
        return isThisNodeResponsibleForTask(getServiceId(), "events cleanup");
    }
}
