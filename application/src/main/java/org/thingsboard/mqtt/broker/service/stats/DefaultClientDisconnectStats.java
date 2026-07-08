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
package org.thingsboard.mqtt.broker.service.stats;

import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

public class DefaultClientDisconnectStats implements ClientDisconnectStats {

    private final DefaultCounter clientDisconnectCounter;

    public DefaultClientDisconnectStats(StatsFactory statsFactory) {
        this.clientDisconnectCounter = statsFactory.createDefaultCounter(StatsType.CLIENT_DISCONNECTS.getPrintName());
    }

    @Override
    public void increment(DisconnectReasonType reasonType) {
        // CE records a single untagged clientDisconnects counter — the reason is deliberately not used as a
        // Micrometer tag. The reason breakdown (tagging + persistence) is a PE-only extension that overrides
        // this class; DisconnectReasonType stays on the shared seam so PE can extend without touching callers.
        clientDisconnectCounter.increment();
    }

    @Override
    public int getCount() {
        return clientDisconnectCounter.get();
    }

    @Override
    public void reset() {
        clientDisconnectCounter.clear();
    }
}
