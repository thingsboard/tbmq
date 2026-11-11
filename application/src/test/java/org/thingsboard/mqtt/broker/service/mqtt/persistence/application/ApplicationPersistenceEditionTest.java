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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads class metadata only: no class is loaded into Spring and no condition is evaluated.
 */
class ApplicationPersistenceEditionTest {

    private static final String PATTERN = "classpath*:org/thingsboard/mqtt/broker/service/mqtt/persistence/application/**/*.class";
    private static final String COMPONENT = "org.springframework.stereotype.Component";
    private static final String CLUSTER = "org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent";
    private static final String STANDALONE = "org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent";

    @Test
    void givenApplicationPersistencePackage_whenReadMetadata_thenBeansAreEditionAnnotated() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);

        List<String> cluster = new ArrayList<>();
        List<String> standIns = new ArrayList<>();
        List<String> unmarked = new ArrayList<>();
        for (Resource resource : resolver.getResources(PATTERN)) {
            MetadataReader reader = readers.getMetadataReader(resource);
            var metadata = reader.getAnnotationMetadata();
            if (!metadata.hasMetaAnnotation(COMPONENT) && !metadata.hasAnnotation(COMPONENT)) {
                continue;
            }
            String name = reader.getClassMetadata().getClassName();
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            if (simpleName.startsWith("NoOp")) {
                (metadata.hasAnnotation(STANDALONE) ? standIns : unmarked).add(name);
            } else {
                (metadata.hasAnnotation(CLUSTER) ? cluster : unmarked).add(name);
            }
        }

        assertThat(unmarked).as("components without the matching edition annotation").isEmpty();
        assertThat(cluster).hasSize(12);
        assertThat(standIns).hasSize(4);
    }

}
