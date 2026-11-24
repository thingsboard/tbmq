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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the committed api/openapi-lite.json, the REST API of the single-node edition, in sync.
 * Regenerate with: mvn test -pl lite-tests -am -Dtest=LiteOpenApiSpecTest -Dsurefire.failIfNoSpecifiedTests=false -Dtbmq.openapi.update=true
 * <p>
 * It is not a {@code *ControllerTest}, so {@code LiteControllerTestSuite} does not pick it up; surefire runs it by its
 * {@code *Test} name.
 */
public class LiteOpenApiSpecTest extends AbstractOpenApiSpecTest {

    private static final String SPEC_PATH_PROPERTY = "tbmq.openapi-lite.spec.path";
    private static final List<String> CLUSTER_ONLY_PREFIXES = List.of(
            "/api/integration",
            "/api/app/shared/subs",
            "/api/app/cluster-info",
            "/api/app/consumer-groups",
            "/api/app/kafka-topics",
            "/api/app/remove-topics",
            "/api/events",
            "/api/node/drain"
    );

    @Override
    protected Path specPath() {
        return Path.of(System.getProperty(SPEC_PATH_PROPERTY, "../api/openapi-lite.json"));
    }

    @Override
    protected String regenerateHint() {
        return "mvn test -pl lite-tests -am -Dtest=LiteOpenApiSpecTest -Dsurefire.failIfNoSpecifiedTests=false -D" + UPDATE_PROPERTY + "=true";
    }

    @Test
    public void givenLiteApi_thenNoClusterOnlyPaths() throws Exception {
        List<String> paths = new ArrayList<>();
        JsonNode spec = JacksonUtil.toJsonNode(fetchSpec());
        spec.get("paths").fieldNames().forEachRemaining(paths::add);

        assertThat(paths).contains("/api/system/backup");
        for (String prefix : CLUSTER_ONLY_PREFIXES) {
            assertThat(paths).as("paths under %s exist only in the cluster edition", prefix)
                    .noneMatch(path -> path.startsWith(prefix));
        }
    }
}
