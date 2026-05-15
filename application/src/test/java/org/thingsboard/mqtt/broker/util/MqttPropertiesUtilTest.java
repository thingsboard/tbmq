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

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

import static org.junit.Assert.assertEquals;

public class MqttPropertiesUtilTest {

    @Test
    public void getReceiveMaxValue_zeroFromClient_returnsOneAndLogsWarn() {
        // MQTT-5 §3.1.2.11.3 makes Receive Maximum=0 a protocol error.
        // We choose to defensively floor to 1 rather than DISCONNECT 0x82.
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(BrokerConstants.RECEIVE_MAXIMUM_PROP_ID, 0));
        assertEquals(1, MqttPropertiesUtil.getReceiveMaxValue(props));
    }

    @Test
    public void getReceiveMaxValue_normalValue_returnsIt() {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(BrokerConstants.RECEIVE_MAXIMUM_PROP_ID, 50));
        assertEquals(50, MqttPropertiesUtil.getReceiveMaxValue(props));
    }

    @Test
    public void getReceiveMaxValue_absent_returnsDefault() {
        MqttProperties props = new MqttProperties();
        assertEquals(BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, MqttPropertiesUtil.getReceiveMaxValue(props));
    }
}
