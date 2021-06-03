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
package org.thingsboard.mqtt.broker.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeStateServiceImpl implements NodeStateService, NodeStateUpdateService {
    private volatile Set<String> connectedServiceIds = Collections.emptySet();

    private final ServiceInfoProvider serviceInfoProvider;

    @PostConstruct
    public void init() {
        connectedServiceIds = Collections.singleton(serviceInfoProvider.getServiceId());
    }

    @Override
    public boolean isConnected(String serviceId) {
        return connectedServiceIds.contains(serviceId);
    }

    @Override
    public synchronized void loadNodes(Collection<String> otherServices) {
        HashSet<String> newConnectedServiceIds = new HashSet<>(connectedServiceIds);
        newConnectedServiceIds.add(serviceInfoProvider.getServiceId());
        connectedServiceIds = newConnectedServiceIds;
    }
}
