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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class ApplicationPubRelMsgCtx {

    private final Set<PersistedPubRelMsg> pubRelMessagesToDeliver;

    public void addPubRelMsg(PersistedPubRelMsg pubRelMsg) {
        pubRelMessagesToDeliver.add(pubRelMsg);
    }

    public boolean nothingToDeliver() {
        return pubRelMessagesToDeliver.isEmpty();
    }

    public List<PersistedPubRelMsg> toSortedPubRelMessagesToDeliver() {
        if (CollectionUtils.isEmpty(pubRelMessagesToDeliver)) {
            return null;
        }
        return pubRelMessagesToDeliver.stream()
                .sorted(Comparator.comparingLong(PersistedMsg::getPacketOffset))
                .collect(Collectors.toList());
    }
}
