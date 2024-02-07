/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.ttl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCleanUpService {

    private final TbQueueAdmin tbQueueAdmin;
    private final ServiceInfoProvider serviceInfoProvider;

    protected boolean isCurrentNodeShouldCleanUp() {
        String serviceId = serviceInfoProvider.getServiceId();

        List<String> brokerServiceIds = tbQueueAdmin.getBrokerServiceIds().stream().sorted().collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(brokerServiceIds)) {
            String firstServiceId = brokerServiceIds.get(0);
            return serviceId.equals(firstServiceId);
        }
        log.info("[{}] Could not find all service ids, proceeding with clean up!", serviceId);
        return true;
    }
}
