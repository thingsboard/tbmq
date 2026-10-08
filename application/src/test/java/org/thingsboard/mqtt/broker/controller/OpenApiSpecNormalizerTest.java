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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiSpecNormalizerTest {

    private static final String RAW_SPEC = "{\"openapi\":\"3.1.0\"," +
            "\"info\":{\"title\":\"TBMQ REST API\",\"version\":\"2.5.0-SNAPSHOT\"}," +
            "\"servers\":[{\"url\":\"http://localhost\",\"description\":\"Generated server url\"}]," +
            "\"paths\":{\"/api/b\":{\"get\":{\"tags\":[\"x\",\"y\"]}},\"/api/a\":{\"get\":{\"tags\":[\"z\"]}}}}";

    @Test
    public void givenSpecWithServers_whenNormalize_thenServersRemoved() throws Exception {
        assertThat(OpenApiSpecNormalizer.normalize(RAW_SPEC)).doesNotContain("servers").doesNotContain("localhost");
    }

    @Test
    public void givenSnapshotVersion_whenNormalize_thenSnapshotSuffixStripped() throws Exception {
        assertThat(OpenApiSpecNormalizer.normalize(RAW_SPEC))
                .contains("\"version\": \"2.5.0\"")
                .doesNotContain("SNAPSHOT");
    }

    @Test
    public void givenReleaseVersion_whenNormalize_thenVersionUnchanged() throws Exception {
        String raw = RAW_SPEC.replace("2.5.0-SNAPSHOT", "2.4.0");
        assertThat(OpenApiSpecNormalizer.normalize(raw)).contains("\"version\": \"2.4.0\"");
    }

    @Test
    public void givenSpec_whenNormalize_thenKeyOrderPreserved() throws Exception {
        String normalized = OpenApiSpecNormalizer.normalize(RAW_SPEC);
        assertThat(normalized.indexOf("\"openapi\"")).isLessThan(normalized.indexOf("\"info\""));
        assertThat(normalized.indexOf("\"info\"")).isLessThan(normalized.indexOf("\"paths\""));
        assertThat(normalized.indexOf("\"/api/b\"")).isLessThan(normalized.indexOf("\"/api/a\""));
    }

    @Test
    public void givenSpec_whenNormalize_thenLfOnlyTwoSpaceIndentTrailingNewlineArraysOnePerLine() throws Exception {
        String normalized = OpenApiSpecNormalizer.normalize(RAW_SPEC);
        assertThat(normalized).doesNotContain("\r");
        assertThat(normalized).startsWith("{\n  \"openapi\": \"3.1.0\",\n  \"info\": {\n    \"title\"");
        assertThat(normalized).endsWith("}\n");
        assertThat(normalized).contains("\"tags\": [\n          \"x\",\n          \"y\"\n        ]");
    }

    @Test
    public void givenNormalizedSpec_whenNormalizeAgain_thenUnchanged() throws Exception {
        String once = OpenApiSpecNormalizer.normalize(RAW_SPEC);
        assertThat(OpenApiSpecNormalizer.normalize(once)).isEqualTo(once);
    }
}
