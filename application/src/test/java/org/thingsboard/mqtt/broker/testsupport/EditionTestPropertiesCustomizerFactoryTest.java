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

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EditionTestPropertiesCustomizerFactoryTest {

    private final EditionTestPropertiesCustomizerFactory factory = new EditionTestPropertiesCustomizerFactory();

    @Test
    void theAnnotationReadsTheSharedTestPropertiesOnly() {
        TestPropertySource testPropertySource = AnnotatedElementUtils.findMergedAnnotation(Annotated.class, TestPropertySource.class);

        assertThat(testPropertySource).isNotNull();
        assertThat(testPropertySource.locations()).containsExactly("classpath:application-test.properties");
        assertThat(testPropertySource.properties()).isEmpty();
    }

    @Test
    void theFactoryIsRegistered() {
        assertThat(SpringFactoriesLoader.forDefaultResourceLocation().load(ContextCustomizerFactory.class))
                .hasAtLeastOneElementOfType(EditionTestPropertiesCustomizerFactory.class);
    }

    @Test
    void givenAClassWithoutTheAnnotation_thenNoCustomizer() {
        assertThat(factory.createContextCustomizer(NotAnnotated.class, List.of())).isNull();
    }

    @Test
    void givenAnAnnotatedClassOrASubclass_thenEqualCustomizers() {
        ContextCustomizer annotated = factory.createContextCustomizer(Annotated.class, List.of());
        ContextCustomizer subclass = factory.createContextCustomizer(AnnotatedSubclass.class, List.of());

        assertThat(annotated).isNotNull().isEqualTo(subclass);
        assertThat(annotated.hashCode()).isEqualTo(subclass.hashCode());
    }

    @Test
    void theCustomizerAddsTheEditionPropertiesRightBelowTheInlinedTestProperties() {
        GenericApplicationContext context = new GenericApplicationContext();
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        sources.addFirst(new MapPropertySource("class path resource [application-test.properties]", Map.of("a", "file")));
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "a=inlined");

        factory.createContextCustomizer(Annotated.class, List.of()).customizeContext(context, null);

        assertThat(sources.stream().map(PropertySource::getName).toList()).startsWith(
                TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME,
                EditionTestPropertiesCustomizerFactory.PROPERTY_SOURCE_NAME,
                "class path resource [application-test.properties]");
        assertThat(sources.get(EditionTestPropertiesCustomizerFactory.PROPERTY_SOURCE_NAME).getSource())
                .isEqualTo(EditionTestInfrastructure.load().contextProperties());
        assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                .isEqualTo(EditionTestInfrastructure.load().contextProperties().get("spring.datasource.url"));
        assertThat(context.getEnvironment().getProperty("a")).isEqualTo("inlined");
    }

    @EditionTest
    static class Annotated {
    }

    static class AnnotatedSubclass extends Annotated {
    }

    static class NotAnnotated {
    }

}
