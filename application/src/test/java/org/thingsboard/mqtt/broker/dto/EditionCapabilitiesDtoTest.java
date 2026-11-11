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
package org.thingsboard.mqtt.broker.dto;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;

import static org.assertj.core.api.Assertions.assertThat;

class EditionCapabilitiesDtoTest {

    @Test
    void givenClusterEdition_whenCreateCapabilities_thenAllExceptBridgeEnabled() {
        assertThat(EditionCapabilitiesDto.of(TbmqEdition.CLUSTER)).isEqualTo(EditionCapabilitiesDto.builder()
                .applicationClients(true).kafka(true).cluster(true).integrations(true).nodeDrain(true).bridge(false).build());
    }

    @Test
    void givenLiteEdition_whenCreateCapabilities_thenAllDisabled() {
        assertThat(EditionCapabilitiesDto.of(TbmqEdition.LITE)).isEqualTo(EditionCapabilitiesDto.builder().build());
    }

    @Test
    void givenClusterCapabilities_whenSerialize_thenJsonShapeIsStable() {
        assertThat(JacksonUtil.toString(EditionCapabilitiesDto.of(TbmqEdition.CLUSTER)))
                .isEqualTo("{\"applicationClients\":true,\"kafka\":true,\"cluster\":true,\"integrations\":true,\"nodeDrain\":true,\"bridge\":false}");
    }

}
