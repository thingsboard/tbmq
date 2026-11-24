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
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps a committed OpenAPI spec in sync with the REST API of the edition under test.
 * A subclass names the committed file and the command that regenerates it.
 * <p>
 * Most controllers serve both editions, so a REST API change usually changes both api/openapi.json and
 * api/openapi-lite.json: regenerate both, with {@link #REGENERATE_CLUSTER} and {@link #REGENERATE_LITE}.
 */
@Slf4j
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
public abstract class AbstractOpenApiSpecTest extends AbstractControllerTest {

    private static final String API_DOCS_URL = "/v3/api-docs/TBMQ";
    protected static final String UPDATE_PROPERTY = "tbmq.openapi.update";
    protected static final String REGENERATE_CLUSTER =
            "mvn test -pl application -Dtest=OpenApiSpecTest -D" + UPDATE_PROPERTY + "=true";
    protected static final String REGENERATE_LITE =
            "mvn test -pl lite-tests -am -Dtest=LiteOpenApiSpecTest -Dsurefire.failIfNoSpecifiedTests=false -D" + UPDATE_PROPERTY + "=true";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * The committed spec file of the edition under test.
     */
    protected abstract Path specPath();

    /**
     * The command that regenerates {@link #specPath()}, quoted in the failure message.
     */
    protected abstract String regenerateHint();

    @Test
    public void givenBrokerApi_whenGenerateSpec_thenCommittedSpecIsUpToDate() throws Exception {
        String generated = OpenApiSpecNormalizer.normalize(fetchSpec());
        Path specPath = specPath().toAbsolutePath().normalize();

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(specPath.getParent());
            Files.writeString(specPath, generated, StandardCharsets.UTF_8);
            log.info("Updated OpenAPI spec at {}", specPath);
            return;
        }

        String committed = Files.exists(specPath) ? Files.readString(specPath, StandardCharsets.UTF_8) : null;
        if (!generated.equals(committed)) {
            Path generatedPath = Path.of("target", specPath.getFileName().toString()).toAbsolutePath();
            Files.createDirectories(generatedPath.getParent());
            Files.writeString(generatedPath, generated, StandardCharsets.UTF_8);
            fail((committed == null ? specPath + " does not exist" : specPath + " is out of date") +
                    ". The generated spec is at " + generatedPath +
                    ". Regenerate with: " + regenerateHint() + " and commit the result." +
                    " Most controllers serve both editions, so regenerate the other edition's spec too: " +
                    (REGENERATE_LITE.equals(regenerateHint()) ? REGENERATE_CLUSTER : REGENERATE_LITE));
        }
    }

    @Test
    public void givenDocumentedHandlers_thenNoTwoShareSamePathAndMethod() {
        Map<String, List<String>> handlersByOperation = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(Hidden.class) || handlerMethod.getBeanType().isAnnotationPresent(Hidden.class)) {
                return;
            }
            for (String pattern : mapping.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
                    handlersByOperation.computeIfAbsent(method + " " + pattern, k -> new ArrayList<>())
                            .add(handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName());
                }
            }
        });
        Map<String, List<String>> collisions = new TreeMap<>();
        handlersByOperation.forEach((operation, handlers) -> {
            if (handlers.size() > 1) {
                collisions.put(operation, handlers);
            }
        });
        assertThat(collisions)
                .as("Handlers sharing path + method collapse into one OpenAPI operation. " +
                        "Give the lookup its own /by-<key> path and keep the old mapping as a @Hidden *Legacy method.")
                .isEmpty();
    }

    @Test
    public void givenGeneratedSpec_thenNoGeneratorHostileConstructs() throws Exception {
        JsonNode spec = JacksonUtil.toJsonNode(fetchSpec());
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
            if (path.getKey().contains("{?")) {
                problems.add("RFC 6570 query template in path key: " + path.getKey());
            }
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                JsonNode responses = operation.getValue().get("responses");
                if (responses == null) {
                    continue;
                }
                responses.fieldNames().forEachRemaining(code -> {
                    if (!code.matches("\\d{3}|[1-5]XX|default")) {
                        problems.add("Invalid response code '" + code + "' in " + operation.getKey() + " " + path.getKey());
                    }
                });
            }
        }
        for (Map.Entry<String, JsonNode> section : spec.path("components").properties()) {
            section.getValue().fieldNames().forEachRemaining(name -> {
                if (!name.matches("[a-zA-Z0-9.\\-_]+")) {
                    problems.add("Invalid component name '" + name + "' in components." + section.getKey());
                }
            });
        }
        JsonNode schemas = spec.path("components").path("schemas");
        for (Map.Entry<String, JsonNode> schema : schemas.properties()) {
            List<JsonNode> parts = new ArrayList<>(List.of(schema.getValue()));
            schema.getValue().path("allOf").forEach(parts::add);
            for (JsonNode part : parts) {
                for (Map.Entry<String, JsonNode> property : part.path("properties").properties()) {
                    // openapi-generator names an inline oneOf model <Schema>_<property>, i.e. class <Schema><Property>
                    String inlineModelName = schema.getKey() + StringUtils.capitalize(property.getKey());
                    if (property.getValue().has("oneOf") && !property.getValue().has("title") && schemas.has(inlineModelName)) {
                        problems.add("Inline oneOf of " + schema.getKey() + "." + property.getKey() +
                                " would be generated as model " + inlineModelName + ", overwriting the schema of that name");
                    }
                }
            }
        }
        for (Map.Entry<String, JsonNode> schema : schemas.properties()) {
            JsonNode discriminator = schema.getValue().path("discriminator");
            if (discriminator.isMissingNode()) {
                continue;
            }
            // Without a mapping, generated clients use schema names (ClientIdBlockedClient) instead of the server's values (CLIENT_ID)
            List<String> mappedRefs = new ArrayList<>();
            discriminator.path("mapping").forEach(ref -> mappedRefs.add(ref.asText()));
            String parentRef = "#/components/schemas/" + schema.getKey();
            for (Map.Entry<String, JsonNode> subtype : schemas.properties()) {
                for (JsonNode part : subtype.getValue().path("allOf")) {
                    String subtypeRef = "#/components/schemas/" + subtype.getKey();
                    if (parentRef.equals(part.path("$ref").asText()) && !mappedRefs.contains(subtypeRef)) {
                        problems.add("Discriminator of " + schema.getKey() + " has no mapping to subtype " + subtype.getKey());
                    }
                }
            }
        }
        // A body must match exactly one oneOf branch: two branches that require nothing and carry no discriminator
        // both accept any common body, so every response fails the schema. Overlapping alternatives need anyOf
        for (JsonNode oneOf : spec.findValues("oneOf")) {
            List<String> openBranches = new ArrayList<>();
            for (JsonNode branch : oneOf) {
                String ref = branch.path("$ref").asText();
                JsonNode branchSchema = ref.isEmpty() ? branch : schemas.path(ref.substring(ref.lastIndexOf('/') + 1));
                boolean hasDiscriminatorParent = false;
                for (JsonNode part : branchSchema.path("allOf")) {
                    String partRef = part.path("$ref").asText();
                    if (!partRef.isEmpty() && schemas.path(partRef.substring(partRef.lastIndexOf('/') + 1)).has("discriminator")) {
                        hasDiscriminatorParent = true;
                    }
                }
                if (!branchSchema.has("required") && !branchSchema.has("discriminator") && !hasDiscriminatorParent) {
                    openBranches.add(ref.isEmpty() ? branch.toString() : ref);
                }
            }
            if (openBranches.size() > 1) {
                problems.add("oneOf branches " + openBranches + " cannot be told apart; use anyOf");
            }
        }
        if (spec.findValuesAsText("type").contains("any")) {
            problems.add("Schema with invalid type 'any'");
        }
        assertThat(problems).isEmpty();
    }

    protected String fetchSpec() throws Exception {
        return doGet(API_DOCS_URL)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
