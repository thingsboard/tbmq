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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import org.thingsboard.mqtt.MqttClientConfig;

/**
 * The {@link MqttClientConfig.RetransmissionConfig} {@code MqttIntegration} sets on its client. The broker's own
 * test clients in {@code application} deliberately use a wider window of their own
 * ({@code AbstractPubSubIntegrationTest}).
 * <p>
 * netty-mqtt's {@code MqttClientImpl} starts a {@code RetransmissionHandler} unconditionally after every
 * SUBSCRIBE/UNSUBSCRIBE and every QoS &gt; 0 PUBLISH is written to the channel. {@code RetransmissionHandler.start}
 * calls {@code startTimer}, which dereferences {@link MqttClientConfig#getRetransmissionConfig()} synchronously
 * while computing the first backoff delay - before {@code eventLoop.schedule(...)} is ever reached, so there is no
 * timer tick to wait for. A null config (the field has no default in either {@code MqttClientConfig} constructor)
 * NPEs right there.
 * <p>
 * For a SUBSCRIBE this NPE is thrown synchronously on the handler thread and fails the subscribe outright. For a
 * QoS &gt; 0 PUBLISH it is thrown inside the channel-future listener that arms the retransmission timer - a
 * different listener from the one that completes the publish's promise, which for QoS &gt; 0 is instead completed
 * later by {@code MqttChannelHandler.handlePuback} via the {@code pendingPublishes} map. So the publish still succeeds
 * and the caller's callback still fires normally; what is actually lost is the retransmission safety net for that
 * PUBLISH - if its PUBACK is genuinely lost on the wire, nothing ever resends it - and the NPE itself surfaces only
 * as a WARN netty logs when notifying the channel-future's listeners, never as a thrown or reported error. The
 * integration's unit tests catch none of this: they mock {@code MqttClient.on(...)}/{@code publish(...)} entirely,
 * so {@code MqttClientImpl}'s real internals never run.
 * <p>
 * These three values are not a library default - none exists - they are the platform's own convention:
 * {@code mqtt.client.retransmission.{max_attempts,initial_delay_millis,jitter_factor}} = {@code 3 / 5000 / 0.15}
 * in the ThingsBoard PE {@code thingsboard.yml}, the same triple ThingsBoard's own black-box tests
 * ({@code MqttClientTest}, {@code MqttGatewayClientTest}) hardcode with the comment "same as defaults in
 * thingsboard.yml". Making this YAML-configurable here is a follow-up, not part of this fix.
 */
public final class MqttClientRetransmissionDefaults {

    public static final MqttClientConfig.RetransmissionConfig CONFIG =
            new MqttClientConfig.RetransmissionConfig(3, 5000L, 0.15d);

    private MqttClientRetransmissionDefaults() {
    }
}
