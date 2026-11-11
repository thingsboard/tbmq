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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TbmqEditionConfigurationTest {

    @TempDir
    Path tmp;

    private final TbmqEditionConfiguration config = new TbmqEditionConfiguration();

    @Test
    void givenClusterMarker_whenCreateBean_thenCluster() {
        config.setBeanClassLoader(markers(tmp, "cluster"));

        assertThat(config.tbmqEdition(new MockEnvironment())).isEqualTo(TbmqEdition.CLUSTER);
    }

    @Test
    void givenLiteMarker_whenCreateBean_thenLite() {
        config.setBeanClassLoader(markers(tmp, "lite"));

        assertThat(config.tbmqEdition(new MockEnvironment())).isEqualTo(TbmqEdition.LITE);
    }

    @Test
    void givenLiteMarkerAndExplicitTbmqServiceType_whenCreateBean_thenLite() {
        config.setBeanClassLoader(markers(tmp, "lite"));

        assertThat(config.tbmqEdition(new MockEnvironment().withProperty("service.type", "tbmq")))
                .isEqualTo(TbmqEdition.LITE);
    }

    @Test
    void givenLiteMarkerAndOtherServiceType_whenCreateBean_thenFail() {
        config.setBeanClassLoader(markers(tmp, "lite"));

        assertThatThrownBy(() -> config.tbmqEdition(new MockEnvironment().withProperty("service.type", "tbmq-integration-executor")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TBMQ Lite runs only with service.type=tbmq, got tbmq-integration-executor");
    }

    @Test
    void givenClusterMarkerAndOtherServiceType_whenCreateBean_thenCluster() {
        config.setBeanClassLoader(markers(tmp, "cluster"));

        assertThat(config.tbmqEdition(new MockEnvironment().withProperty("service.type", "tbmq-integration-executor")))
                .isEqualTo(TbmqEdition.CLUSTER);
    }

    @Test
    void givenApplicationClasspath_whenCreateBean_thenClusterFromQueueKafkaMarker() {
        config.setBeanClassLoader(getClass().getClassLoader());

        assertThat(config.tbmqEdition(new MockEnvironment())).isEqualTo(TbmqEdition.CLUSTER);
    }

    // Parent is null so that the real cluster marker on this classpath stays invisible.
    private static ClassLoader markers(Path dir, String content) {
        try {
            Path root = Files.createTempDirectory(dir, "markers");
            Path marker = root.resolve(TbmqEditionResolver.MARKER_RESOURCE);
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, content, StandardCharsets.UTF_8);
            return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
