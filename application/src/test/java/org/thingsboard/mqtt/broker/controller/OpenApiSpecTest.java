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

import java.nio.file.Path;

/**
 * Keeps the committed api/openapi.json in sync with the REST API.
 * Regenerate with: mvn test -pl application -Dtest=OpenApiSpecTest -Dtbmq.openapi.update=true
 */
public class OpenApiSpecTest extends AbstractOpenApiSpecTest {

    private static final String SPEC_PATH_PROPERTY = "tbmq.openapi.spec.path";

    @Override
    protected Path specPath() {
        return Path.of(System.getProperty(SPEC_PATH_PROPERTY, "../api/openapi.json"));
    }

    @Override
    protected String regenerateHint() {
        return "mvn test -pl application -Dtest=OpenApiSpecTest -D" + UPDATE_PROPERTY + "=true";
    }
}
