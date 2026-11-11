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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.thingsboard.mqtt.broker.common.util.edition.EditionMarkers.markers;

class TbmqEditionResolverTest {

    @TempDir
    Path tmp;

    @Test
    void givenClusterMarker_whenResolve_thenCluster() {
        assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "cluster"))).isEqualTo(TbmqEdition.CLUSTER);
    }

    @Test
    void givenLiteMarkerWithTrailingNewline_whenResolve_thenLite() {
        assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "lite\n"))).isEqualTo(TbmqEdition.LITE);
    }

    @Test
    void givenMarkerWithCommentHeader_whenResolve_thenCommentIgnored() {
        assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "# header\ncluster"))).isEqualTo(TbmqEdition.CLUSTER);
    }

    @Test
    void givenDuplicateMarkersWithSameValue_whenResolve_thenOk() {
        assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "cluster", "cluster"))).isEqualTo(TbmqEdition.CLUSTER);
    }

    @Test
    void givenNoMarker_whenResolve_thenThrowsNamingTheResource() {
        assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("META-INF/tbmq-edition");
    }

    @Test
    void givenConflictingMarkers_whenResolve_thenThrowsListingBothValues() {
        assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null, "cluster", "lite")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster")
                .hasMessageContaining("lite");
    }

    @Test
    void givenUnknownMarkerValue_whenResolve_thenThrowsNamingTheValue() {
        assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null, "edge")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("edge");
    }

    @Test
    void givenMarkerWithoutValueLine_whenResolve_thenThrowsNamingTheFile() {
        for (String content : new String[]{"", "  \n\t\n", "# only a comment\n# another\n"}) {
            ClassLoader classLoader = markers(tmp, null, content);
            assertThatThrownBy(() -> TbmqEditionResolver.resolve(classLoader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("META-INF/tbmq-edition")
                    .hasMessageContaining("file:");
        }
    }

    @Test
    void givenFailedResolution_whenResolveAgain_thenThrowsAgain() {
        ClassLoader classLoader = markers(tmp, null, "cluster", "lite");
        assertThatThrownBy(() -> TbmqEditionResolver.resolve(classLoader)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TbmqEditionResolver.resolve(classLoader)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenSameClassLoader_whenResolveTwice_thenSameResult() {
        ClassLoader classLoader = markers(tmp, null, "lite");
        assertThat(TbmqEditionResolver.resolve(classLoader)).isEqualTo(TbmqEdition.LITE);
        assertThat(TbmqEditionResolver.resolve(classLoader)).isEqualTo(TbmqEdition.LITE);
    }

    @Test
    void givenKnownValues_whenFromMarkerValue_thenMapped() {
        assertThat(TbmqEdition.fromMarkerValue("cluster")).isEqualTo(TbmqEdition.CLUSTER);
        assertThat(TbmqEdition.fromMarkerValue("lite")).isEqualTo(TbmqEdition.LITE);
        assertThat(TbmqEdition.CLUSTER.getMarkerValue()).isEqualTo("cluster");
    }

}
