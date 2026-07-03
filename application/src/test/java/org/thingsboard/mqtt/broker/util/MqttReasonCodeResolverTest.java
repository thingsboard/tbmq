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
package org.thingsboard.mqtt.broker.util;

import io.netty.handler.codec.mqtt.MqttReasonCodes.UnsubAck;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Test;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MqttReasonCodeResolverTest {

    @Test
    public void givenMqtt5_whenUnsubAckSuccess_thenReturnsSuccess() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        assertThat(MqttReasonCodeResolver.unsubAckSuccess(ctx)).isEqualTo(UnsubAck.SUCCESS);
    }

    @Test
    public void givenMqtt311_whenUnsubAckSuccess_thenReturnsNull() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);

        assertThat(MqttReasonCodeResolver.unsubAckSuccess(ctx)).isNull();
    }

    @Test
    public void givenMqtt5_whenUnsubAckNoSubscriptionExisted_thenReturnsNoSubscriptionExisted() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        assertThat(MqttReasonCodeResolver.unsubAckNoSubscriptionExisted(ctx)).isEqualTo(UnsubAck.NO_SUBSCRIPTION_EXISTED);
    }

    @Test
    public void givenMqtt311_whenUnsubAckNoSubscriptionExisted_thenReturnsNull() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);

        assertThat(MqttReasonCodeResolver.unsubAckNoSubscriptionExisted(ctx)).isNull();
    }
}
