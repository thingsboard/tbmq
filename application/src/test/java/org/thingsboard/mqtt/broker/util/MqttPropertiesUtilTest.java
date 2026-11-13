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
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;

import static org.junit.Assert.assertEquals;

public class MqttPropertiesUtilTest {

    @Test
    public void getReceiveMaxValue_zeroFromClient_returnsZero() {
        // MQTT-5 §3.1.2.11.3 makes Receive Maximum=0 a protocol error. The util returns the raw value;
        // ConnectServiceImpl is responsible for rejecting the CONNECT with CONNACK 0x82.
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(BrokerConstants.RECEIVE_MAXIMUM_PROP_ID, 0));
        assertEquals(0, MqttPropertiesUtil.getReceiveMaxValue(props));
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

    @Test
    public void getConnectSessionExpiryIntervalValue_neverExpires_returnsMax() {
        // MQTT-5 §3.1.2.11.2: the interval is a Four Byte Integer (unsigned); 0xFFFFFFFF means the Session does not expire
        assertEquals(604800, MqttPropertiesUtil.getConnectSessionExpiryIntervalValue(sessionExpiry(0xFFFFFFFF), 604800));
    }

    @Test
    public void getConnectSessionExpiryIntervalValue_aboveSignedIntMax_returnsMax() {
        assertEquals(604800, MqttPropertiesUtil.getConnectSessionExpiryIntervalValue(sessionExpiry(0x80000000), 604800));
    }

    @Test
    public void getConnectSessionExpiryIntervalValue_belowMax_returnsIt() {
        assertEquals(3600, MqttPropertiesUtil.getConnectSessionExpiryIntervalValue(sessionExpiry(3600), 604800));
    }

    @Test
    public void getDisconnectSessionExpiryIntervalValue_neverExpires_returnsMax() {
        assertEquals(604800, MqttPropertiesUtil.getDisconnectSessionExpiryIntervalValue(sessionExpiry(0xFFFFFFFF), 604800));
    }

    @Test
    public void getDisconnectSessionExpiryIntervalValue_absent_returnsNotSet() {
        assertEquals(-1, MqttPropertiesUtil.getDisconnectSessionExpiryIntervalValue(new MqttProperties(), 604800));
    }

    @Test
    public void getMsgExpiryResult_interval_returnsTheWholeSecondsLeft() {
        // MQTT-5 [MQTT-3.3.2-6]: the received value minus the time the message waited, 5.5 s here
        MsgExpiryResult result = MqttPropertiesUtil.getMsgExpiryResult(10, 1_000L, 5_500L);
        assertEquals(new MsgExpiryResult(true, false, 5), result);
    }

    @Test
    public void getMsgExpiryResult_intervalPassed_returnsExpired() {
        assertEquals(new MsgExpiryResult(true, true, 0), MqttPropertiesUtil.getMsgExpiryResult(10, 1_000L, 11_001L));
    }

    @Test
    public void getMsgExpiryResult_zeroInterval_staysZero() {
        assertEquals(new MsgExpiryResult(true, false, 0), MqttPropertiesUtil.getMsgExpiryResult(0, 1_000L, 60_000L));
    }

    @Test
    public void getMsgExpiryResult_properties_matchTheIntervalOverload() {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, 10));
        assertEquals(MqttPropertiesUtil.getMsgExpiryResult(10, 1_000L, 5_500L),
                MqttPropertiesUtil.getMsgExpiryResult(props, 1_000L, 5_500L));
        assertEquals(new MsgExpiryResult(false, false, 0), MqttPropertiesUtil.getMsgExpiryResult(new MqttProperties(), 1_000L, 5_500L));
    }

    private static MqttProperties sessionExpiry(int value) {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(BrokerConstants.SESSION_EXPIRY_INTERVAL_PROP_ID, value));
        return props;
    }
}
