/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SysAdminSettingTypeTest {

    @Test
    void shouldReturnEmptyForInvalidKey() {
        assertThat(SysAdminSettingType.parse("invalid")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForInvalidCase() {
        assertThat(SysAdminSettingType.parse("Mail")).isEmpty(); // expected "mail"
    }

    @Test
    void shouldReturnEmptyForNull() {
        assertThat(SysAdminSettingType.parse(null)).isEmpty();
    }

    @Test
    void shouldReturnValidValueForSupportedTypes() {
        assertThat(SysAdminSettingType.parse("mail"))
                .contains(SysAdminSettingType.MAIL);

        assertThat(SysAdminSettingType.parse("connectivity"))
                .contains(SysAdminSettingType.CONNECTIVITY);

        assertThat(SysAdminSettingType.parse("websocket"))
                .contains(SysAdminSettingType.WEBSOCKET);

        assertThat(SysAdminSettingType.parse("securitySettings"))
                .contains(SysAdminSettingType.SECURITY_SETTINGS);

        assertThat(SysAdminSettingType.parse("mqttAuthorization"))
                .contains(SysAdminSettingType.MQTT_AUTHORIZATION);
    }

}
