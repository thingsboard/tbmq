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
package org.thingsboard.mqtt.broker.service.processing.backpressure;

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

/**
 * Never pauses a connection: in the cluster edition Kafka absorbs the publishes.
 */
@Component
@ClusterComponent
public class NoOpPublishBackpressure implements PublishBackpressure {

    @Override
    public void onPublish(ClientSessionCtx ctx) {
    }

    @Override
    public void onChannelRead(ClientSessionCtx ctx) {
    }

    @Override
    public KeepAliveProbe probe(UUID sessionId, long lastPacketTime) {
        return KeepAliveProbe.CLOSE;
    }

}
