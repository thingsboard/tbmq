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
package org.thingsboard.mqtt.broker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

/**
 * Exposes the {@link TbmqEdition} resolved from the classpath marker. The edition is never read from configuration.
 */
@Slf4j
@Configuration
public class TbmqEditionConfiguration implements BeanClassLoaderAware {

    private ClassLoader beanClassLoader;

    @Override
    public void setBeanClassLoader(ClassLoader beanClassLoader) {
        this.beanClassLoader = beanClassLoader;
    }

    @Bean
    public TbmqEdition tbmqEdition(Environment environment) {
        TbmqEdition edition = TbmqEditionResolver.resolve(beanClassLoader);
        if (edition == TbmqEdition.LITE) {
            String serviceType = environment.getProperty("service.type", "tbmq");
            if (!"tbmq".equals(serviceType)) {
                throw new IllegalStateException("TBMQ Lite runs only with service.type=tbmq, got " + serviceType);
            }
        }
        log.info("TBMQ edition: {}", edition.getMarkerValue());
        return edition;
    }

}
