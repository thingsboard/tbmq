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
package org.thingsboard.mqtt.broker.service.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;
import org.thingsboard.mqtt.broker.standalone.store.ManagementDataStep;
import org.thingsboard.mqtt.broker.standalone.store.StandaloneManagementDataInitializer;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterManagementDataInitializerTest {

    @TempDir
    Path tmp;

    @Test
    void givenClusterMarker_whenStartContext_thenTheOnlyInitializerIsTheClusterOneAndRunsNoStep() throws IOException {
        StepCounter.APPLIED.set(0);

        try (var ctx = startContext(markerLoader("cluster"))) {
            assertThat(ctx.getBeansOfType(ManagementDataInitializer.class)).hasSize(1);
            assertThat(ctx.getBean("managementDataInitializer")).isInstanceOf(ClusterManagementDataInitializer.class);
        }
        assertThat(StepCounter.APPLIED).hasValue(0);
    }

    @Test
    void givenLiteMarker_whenStartContext_thenTheOnlyInitializerIsTheStandaloneOneAndAppliesTheSteps() throws IOException {
        StepCounter.APPLIED.set(0);

        try (var ctx = startContext(markerLoader("lite"))) {
            assertThat(ctx.getBeansOfType(ManagementDataInitializer.class)).hasSize(1);
            assertThat(ctx.getBean("managementDataInitializer")).isInstanceOf(StandaloneManagementDataInitializer.class);
        }
        assertThat(StepCounter.APPLIED).hasValue(1);
    }

    @Test
    void givenLiteMarkerAndNoSteps_whenStartContext_thenTheStandaloneInitializerStillStarts() throws IOException {
        try (var ctx = startContext(markerLoader("lite"), ClusterManagementDataInitializer.class, StandaloneManagementDataInitializer.class)) {
            assertThat(ctx.getBean("managementDataInitializer")).isInstanceOf(StandaloneManagementDataInitializer.class);
        }
    }

    static class StepCounter implements ManagementDataStep {

        static final AtomicInteger APPLIED = new AtomicInteger();

        @Override
        public void apply() {
            APPLIED.incrementAndGet();
        }

    }

    // The condition evaluator captures the class loader when the context is constructed, so the loader is on the bean
    // factory before register(...) is called. The test class path also has the cluster marker; only the given one is visible here.
    private static AnnotationConfigApplicationContext startContext(ClassLoader loader) {
        return startContext(loader, ClusterManagementDataInitializer.class, StandaloneManagementDataInitializer.class, StepCounter.class);
    }

    private static AnnotationConfigApplicationContext startContext(ClassLoader loader, Class<?>... beans) {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(loader);
        var ctx = new AnnotationConfigApplicationContext(beanFactory);
        ctx.setClassLoader(loader);
        try {
            ctx.register(beans);
            ctx.refresh();
        } catch (RuntimeException e) {
            ctx.close();
            throw e;
        }
        return ctx;
    }

    private ClassLoader markerLoader(String edition) throws IOException {
        Path marker = Files.createTempDirectory(tmp, "marker").resolve(TbmqEditionResolver.MARKER_RESOURCE);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, edition, StandardCharsets.UTF_8);
        URL markerUrl = marker.toUri().toURL();
        return new ClassLoader(ClusterManagementDataInitializerTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return TbmqEditionResolver.MARKER_RESOURCE.equals(name) ? Collections.enumeration(List.of(markerUrl)) : super.getResources(name);
            }
        };
    }

}
