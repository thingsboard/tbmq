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

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

public class DefaultConnectionStats implements ConnectionStats {

    private final DefaultCounter acceptedCounter;
    private final DefaultCounter refusedCounter;
    private final DefaultCounter errorCounter;

    public DefaultConnectionStats(StatsFactory statsFactory) {
        this.acceptedCounter = statsFactory.createDefaultCounter(StatsType.CONNECTION_ACCEPTED.getPrintName());
        this.refusedCounter = statsFactory.createDefaultCounter(StatsType.CONNECTION_REFUSED.getPrintName());
        this.errorCounter = statsFactory.createDefaultCounter(StatsType.CONNECTION_ERROR.getPrintName());
    }

    @Override
    public void onConnectionAccepted() {
        acceptedCounter.increment();
    }

    @Override
    public void onConnectionRefused(MqttConnectReturnCode returnCode) {
        // CE records a single untagged connectionRefused counter — the returnCode is deliberately not
        // used as a Micrometer tag. The reason breakdown is a PE-only extension that overrides this class.
        refusedCounter.increment();
    }

    @Override
    public void onConnectionError(ConnectionErrorType type) {
        // CE records a single untagged connectionError counter — the type is deliberately not used as a
        // Micrometer tag. The type breakdown is a PE-only extension that overrides this class.
        errorCounter.increment();
    }

    @Override
    public int getAcceptedCount() {
        return acceptedCounter.get();
    }

    @Override
    public int getRefusedCount() {
        return refusedCounter.get();
    }

    @Override
    public int getErrorCount() {
        return errorCounter.get();
    }

    @Override
    public void reset() {
        acceptedCounter.clear();
        refusedCounter.clear();
        errorCounter.clear();
    }
}
