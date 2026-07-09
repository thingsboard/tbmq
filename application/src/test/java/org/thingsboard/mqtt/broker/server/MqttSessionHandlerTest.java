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
package org.thingsboard.mqtt.broker.server;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MqttSessionHandlerTest {

    // When a broker-side close was recorded for the session, a following IOException/reset is attributed to TBMQ.
    @Test
    public void connectionCloseOrigin_brokerCloseRecorded_attributesToTbmq() {
        assertThat(MqttSessionHandler.connectionCloseOrigin(true))
                .contains("by TBMQ")
                .contains("broker-side close was recorded");
    }

    // With no broker-side close recorded, the reset is attributed (best-effort) to the peer or the network.
    @Test
    public void connectionCloseOrigin_noBrokerClose_attributesToPeerOrNetwork() {
        assertThat(MqttSessionHandler.connectionCloseOrigin(false))
                .contains("remote peer or a network device")
                .contains("external to TBMQ")
                .contains("no broker-side close was recorded");
    }
}
