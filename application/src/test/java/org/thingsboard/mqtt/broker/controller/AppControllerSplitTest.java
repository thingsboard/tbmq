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
package org.thingsboard.mqtt.broker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventProcessor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppControllerSplitTest {

    private static Set<String> mappings(Class<?> controller) {
        String base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class).path()[0];
        Set<String> result = new HashSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            for (var httpMethod : mapping.method()) {
                for (String path : mapping.path()) {
                    result.add(httpMethod + " " + base + path);
                }
            }
        }
        return result;
    }

    @Test
    void givenSplitControllers_whenCollectMappings_thenEveryEndpointIsKeptOnce() {
        Set<String> shared = mappings(AppController.class);
        Set<String> cluster = mappings(ClusterAppController.class);

        assertThat(shared).doesNotContainAnyElementsOf(cluster);
        Set<String> all = new HashSet<>(shared);
        all.addAll(cluster);
        assertThat(all).containsExactlyInAnyOrder(
                "DELETE /api/app/remove-topics", "GET /api/app/cluster-info", "GET /api/app/cluster-info/v2",
                "GET /api/app/kafka-topics", "GET /api/app/kafka-topics/v2", "GET /api/app/consumer-groups",
                "GET /api/app/consumer-groups/v2", "DELETE /api/app/consumer-group", "GET /api/app/config",
                "GET /api/app/listener/port", "GET /api/app/brokers", "GET /api/app/service/info",
                "DELETE /api/app/service/info");
    }

    @Test
    void givenSharedController_whenInspectDependencies_thenNoClusterOnlyDependencies() {
        assertThat(Arrays.stream(AppController.class.getDeclaredConstructors())
                .flatMap(c -> Arrays.stream(c.getParameterTypes())))
                .doesNotContain(TbQueueAdmin.class, ApplicationRemovedEventProcessor.class);
        assertThat(AnnotatedElementUtils.hasAnnotation(AppController.class, ClusterComponent.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(ClusterAppController.class, ClusterComponent.class)).isTrue();
    }
}
