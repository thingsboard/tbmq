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
package org.thingsboard.mqtt.broker.testsupport;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.List;
import java.util.Map;

/**
 * Gives the Spring context of an {@link EditionTest} class the {@link EditionTestInfrastructure#contextProperties()} of
 * the edition on the test classpath, as a property source named {@value #PROPERTY_SOURCE_NAME}. It ranks above
 * every {@code @TestPropertySource} file, {@code application-test.properties} included, and below the inlined test
 * properties.
 * <p>
 * The customizer is part of the context cache key and compares by the properties, so the classes with equal properties
 * still share their cached contexts. Registered in {@code META-INF/spring.factories}.
 */
public class EditionTestPropertiesCustomizerFactory implements ContextCustomizerFactory {

    static final String PROPERTY_SOURCE_NAME = "editionTestProperties";

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        if (!TestContextAnnotationUtils.hasAnnotation(testClass, EditionTest.class)) {
            return null;
        }
        return new EditionTestPropertiesCustomizer(Map.copyOf(EditionTestInfrastructure.load().contextProperties()));
    }

    private record EditionTestPropertiesCustomizer(Map<String, Object> properties) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            MutablePropertySources sources = context.getEnvironment().getPropertySources();
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            if (sources.contains(TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME)) {
                sources.addAfter(TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME, source);
            } else {
                sources.addFirst(source);
            }
        }

    }

}
