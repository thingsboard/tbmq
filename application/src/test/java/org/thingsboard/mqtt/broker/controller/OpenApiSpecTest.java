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

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

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
 * Keeps the committed api/openapi.json in sync with the REST API.
 * Regenerate with: mvn test -pl application -Dtest=OpenApiSpecTest -Dtbmq.openapi.update=true
 */
@Slf4j
@DaoSqlTest
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
public class OpenApiSpecTest extends AbstractControllerTest {

    private static final String API_DOCS_URL = "/v3/api-docs/TBMQ";
    private static final String SPEC_PATH_PROPERTY = "tbmq.openapi.spec.path";
    private static final String UPDATE_PROPERTY = "tbmq.openapi.update";
    private static final String REGENERATE_HINT =
            "mvn test -pl application -Dtest=OpenApiSpecTest -D" + UPDATE_PROPERTY + "=true";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    public void givenBrokerApi_whenGenerateSpec_thenCommittedSpecIsUpToDate() throws Exception {
        String generated = OpenApiSpecNormalizer.normalize(fetchSpec());
        Path specPath = Path.of(System.getProperty(SPEC_PATH_PROPERTY, "../api/openapi.json")).toAbsolutePath().normalize();

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(specPath.getParent());
            Files.writeString(specPath, generated, StandardCharsets.UTF_8);
            log.info("Updated OpenAPI spec at {}", specPath);
            return;
        }

        String committed = Files.exists(specPath) ? Files.readString(specPath, StandardCharsets.UTF_8) : null;
        if (!generated.equals(committed)) {
            Path generatedPath = Path.of("target", "openapi.json").toAbsolutePath();
            Files.createDirectories(generatedPath.getParent());
            Files.writeString(generatedPath, generated, StandardCharsets.UTF_8);
            fail((committed == null ? specPath + " does not exist" : specPath + " is out of date") +
                    ". The generated spec is at " + generatedPath +
                    ". Regenerate with: " + REGENERATE_HINT + " and commit the result.");
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

    private String fetchSpec() throws Exception {
        return doGet(API_DOCS_URL)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
