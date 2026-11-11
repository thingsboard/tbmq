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
package org.thingsboard.mqtt.broker.common.util.edition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.thingsboard.mqtt.broker.common.util.edition.EditionMarkers.markers;

class TbmqEditionConditionTest {

    @TempDir
    Path tmp;

    @Component
    @ClusterComponent
    static class ClusterBean {
    }

    @Component
    @StandaloneComponent
    static class StandaloneBean {
    }

    @Test
    void givenClusterMarker_whenStartContext_thenOnlyClusterBeansRegistered() {
        try (var ctx = startContext(markers(tmp, getClass().getClassLoader(), "cluster"))) {
            assertThat(ctx.getBeansOfType(ClusterBean.class)).hasSize(1);
            assertThat(ctx.getBeansOfType(StandaloneBean.class)).isEmpty();
        }
    }

    @Test
    void givenLiteMarker_whenStartContext_thenOnlyStandaloneBeansRegistered() {
        try (var ctx = startContext(markers(tmp, getClass().getClassLoader(), "lite"))) {
            assertThat(ctx.getBeansOfType(StandaloneBean.class)).hasSize(1);
            assertThat(ctx.getBeansOfType(ClusterBean.class)).isEmpty();
        }
    }

    @Test
    void givenNoMarker_whenStartContext_thenFailsNamingTheMarkerResource() {
        ClassLoader loader = markers(tmp, getClass().getClassLoader());
        assertThatThrownBy(() -> startContext(loader).close())
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("META-INF/tbmq-edition"));
    }

    // The condition evaluator captures its class loader when the context is constructed, so the
    // marker loader has to be on the bean factory before register(...) is called.
    private static AnnotationConfigApplicationContext startContext(ClassLoader loader) {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(loader);
        var ctx = new AnnotationConfigApplicationContext(beanFactory);
        ctx.setClassLoader(loader);
        try {
            ctx.register(ClusterBean.class, StandaloneBean.class);
            ctx.refresh();
        } catch (RuntimeException e) {
            ctx.close();
            throw e;
        }
        return ctx;
    }

}
