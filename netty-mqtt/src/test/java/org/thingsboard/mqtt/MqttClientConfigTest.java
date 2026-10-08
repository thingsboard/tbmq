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
package org.thingsboard.mqtt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttClientConfigTest {

    @Test
    void retransmissionConfigCannotBeSetToNull() {
        var clientConfig = new MqttClientConfig();

        assertThatThrownBy(() -> clientConfig.setRetransmissionConfig(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("retransmissionConfig");
        assertThat(clientConfig.getRetransmissionConfig()).isNotNull();
    }

    @Test
    void keepAliveZeroDisablesItRatherThanBeingRejected() {
        var clientConfig = new MqttClientConfig();

        clientConfig.setTimeoutSeconds(0);

        assertThat(clientConfig.getTimeoutSeconds()).isZero();
    }

    @Test
    void keepAliveMinusOneIsStoredAsZero() {
        var clientConfig = new MqttClientConfig();

        clientConfig.setTimeoutSeconds(-1);

        assertThat(clientConfig.getTimeoutSeconds()).describedAs("-1 always meant no keep-alive, but was sent as 65535").isZero();
    }

    @Test
    void keepAliveAboveTheMqttMaximumIsRejected() {
        var clientConfig = new MqttClientConfig();

        // CONNECT carries the keep-alive in two bytes: 70 000 would go out as 4 464
        assertThatThrownBy(() -> clientConfig.setTimeoutSeconds(65536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65535");
        assertThat(clientConfig.getTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void keepAliveBelowMinusOneIsRejected() {
        var clientConfig = new MqttClientConfig();

        assertThatThrownBy(() -> clientConfig.setTimeoutSeconds(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theConnectTimeoutDefaultsToThirtySeconds() {
        assertThat(new MqttClientConfig().getConnectTimeoutSec()).isEqualTo(30);
    }

    @Test
    void theConnectTimeoutMustBePositive() {
        var clientConfig = new MqttClientConfig();

        assertThatThrownBy(() -> clientConfig.setConnectTimeoutSec(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeoutSec");
    }

}
