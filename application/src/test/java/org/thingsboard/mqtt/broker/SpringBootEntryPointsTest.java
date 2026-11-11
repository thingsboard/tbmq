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
package org.thingsboard.mqtt.broker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.thingsboard.mqtt.broker.dao.JpaDaoConfig;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootEntryPointsTest {

    @ParameterizedTest
    @ValueSource(classes = {ThingsboardMqttBrokerApplication.class, ThingsboardMqttBrokerInstallApplication.class})
    void entryPointEnablesAutoConfigurationWithoutRedis(Class<?> entryPoint) {
        EnableAutoConfiguration ann = AnnotatedElementUtils.findMergedAnnotation(entryPoint, EnableAutoConfiguration.class);
        assertThat(ann).isNotNull();
        assertThat(ann.excludeName()).contains("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    @Test
    void jpaDaoConfigNoLongerEnablesAutoConfiguration() {
        assertThat(AnnotatedElementUtils.hasAnnotation(JpaDaoConfig.class, EnableAutoConfiguration.class)).isFalse();
    }

}
