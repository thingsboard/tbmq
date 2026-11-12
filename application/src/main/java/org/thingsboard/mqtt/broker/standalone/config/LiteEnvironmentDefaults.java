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
package org.thingsboard.mqtt.broker.standalone.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

import java.util.Map;

/**
 * Adds the fixed settings of the lite edition as the highest-precedence property source. The values are
 * placeholders, so a setting the user may still choose (for example {@code standalone.service-id}) applies.
 * The cluster edition, and a classpath without an edition marker, are left untouched.
 */
public class LiteEnvironmentDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "tbmqLiteDefaults";

    static Map<String, Object> defaults() {
        return Map.of("service.id", "${standalone.service-id:tbmq-lite}");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TbmqEdition edition;
        try {
            edition = TbmqEditionResolver.resolve(application.getClassLoader());
        } catch (IllegalStateException e) {
            // no or conflicting markers: the edition condition reports it when the context starts
            return;
        }
        if (edition == TbmqEdition.LITE) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults()));
        }
    }

}
