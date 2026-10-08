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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Turns the spec served at /v3/api-docs into the committed api/openapi.json:
 * environment-independent (no servers, release version) and diff-friendly (stable pretty-print, LF).
 */
final class OpenApiSpecNormalizer {

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultIndenter LF_INDENTER = new DefaultIndenter("  ", "\n");
    private static final ObjectWriter WRITER = MAPPER.writer(new DefaultPrettyPrinter()
            .withObjectIndenter(LF_INDENTER)
            .withArrayIndenter(LF_INDENTER)
            .withSeparators(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER)));

    private OpenApiSpecNormalizer() {
    }

    static String normalize(String rawSpec) throws JsonProcessingException {
        ObjectNode spec = (ObjectNode) MAPPER.readTree(rawSpec);
        spec.remove("servers");
        if (spec.get("info") instanceof ObjectNode info) {
            String version = info.path("version").asText();
            if (version.endsWith(SNAPSHOT_SUFFIX)) {
                info.put("version", version.substring(0, version.length() - SNAPSHOT_SUFFIX.length()));
            }
        }
        return WRITER.writeValueAsString(spec) + "\n";
    }
}
