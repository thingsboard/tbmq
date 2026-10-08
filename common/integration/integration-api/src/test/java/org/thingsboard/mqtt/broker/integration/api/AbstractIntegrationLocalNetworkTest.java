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
package org.thingsboard.mqtt.broker.integration.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared local-network guard, by literal address so no DNS is involved. */
class AbstractIntegrationLocalNetworkTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "0.0.0.0", "10.0.0.5", "172.16.0.1", "192.168.1.1", "169.254.0.1",
            "::1", "::", "fe80::1", "fec0::1",
            // unique-local fc00::/7, which InetAddress.isSiteLocalAddress() does not cover
            "fd12:3456::5", "fc00::1", "fdff:ffff::1"})
    void localAddressesAreLocal(String literal) throws Exception {
        assertThat(AbstractIntegration.isLocalNetworkAddress(InetAddress.getByName(literal))).isTrue();
        assertThat(AbstractIntegration.isLocalNetworkHost(literal)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "2001:4860:4860::8888", "fb00::1", "fe00::1"})
    void publicAddressesAreNotLocal(String literal) throws Exception {
        assertThat(AbstractIntegration.isLocalNetworkAddress(InetAddress.getByName(literal))).isFalse();
        assertThat(AbstractIntegration.isLocalNetworkHost(literal)).isFalse();
    }
}
