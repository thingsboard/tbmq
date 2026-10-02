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
package org.thingsboard.mqtt.broker.integration.api.template;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationTemplateTest {

    private static ObjectNode body() {
        ObjectNode body = JacksonUtil.newObjectNode();
        body.put("clientId", "c1");
        body.put("topicName", "sensors/1");
        body.put("qos", 1);
        body.put("retain", false);
        body.put("ts", 1000L);
        body.put("username", "");
        body.putObject("props").put("site", "A").put("app.version", "1.2");
        body.putObject("metadata").put("integrationName", "kafka").put("device.type", "meter");
        return body;
    }

    @Test
    void givenPlaceholders_whenResolve_thenFilledFromBody() {
        IntegrationTemplate template = IntegrationTemplate.parse("Key", "${clientId}/${topicName}");

        assertThat(template.hasPlaceholders()).isTrue();
        assertThat(template.resolve(body())).contains("c1/sensors/1");
    }

    @Test
    void givenNumberAndBooleanFields_whenResolve_thenInsertedAsText() {
        assertThat(IntegrationTemplate.parse("Key", "${qos}-${retain}-${ts}").resolve(body())).contains("1-false-1000");
    }

    @Test
    void givenNestedPlaceholders_whenResolve_thenFilledFromObjects() {
        assertThat(IntegrationTemplate.parse("Key", "${props.site}:${metadata.integrationName}").resolve(body()))
                .contains("A:kafka");
    }

    @Test
    void givenDottedNestedKey_whenResolve_thenWholeRestIsTheKey() {
        assertThat(IntegrationTemplate.parse("Key", "${props.app.version}:${metadata.device.type}").resolve(body()))
                .contains("1.2:meter");
    }

    @Test
    void givenWhitespaceInsidePlaceholder_whenResolve_thenTrimmed() {
        assertThat(IntegrationTemplate.parse("Key", "${ clientId }").resolve(body())).contains("c1");
    }

    @Test
    void givenMissingField_whenResolve_thenWholeTemplateEmpty() {
        assertThat(IntegrationTemplate.parse("Key", "dev-${clientCertCn}").resolve(body())).isEmpty();
    }

    @Test
    void givenEmptyStringField_whenResolve_thenWholeTemplateEmpty() {
        assertThat(IntegrationTemplate.parse("Key", "${username}").resolve(body())).isEmpty();
    }

    @Test
    void givenMissingNestedKey_whenResolve_thenWholeTemplateEmpty() {
        assertThat(IntegrationTemplate.parse("Key", "${props.missing}").resolve(body())).isEmpty();
    }

    @Test
    void givenJsonNullField_whenResolve_thenWholeTemplateEmpty() {
        ObjectNode body = body();
        body.putNull("clientId");

        assertThat(IntegrationTemplate.parse("Key", "${clientId}").resolve(body)).isEmpty();
    }

    @Test
    void givenStaticTemplate_whenResolveWithNullBody_thenLiteral() {
        IntegrationTemplate template = IntegrationTemplate.parse("Key", "static-key");

        assertThat(template.hasPlaceholders()).isFalse();
        assertThat(template.resolve(null)).contains("static-key");
    }

    @Test
    void givenNullOrEmptyTemplate_whenResolve_thenEmptyStringLiteral() {
        assertThat(IntegrationTemplate.parse("Header 'h'", null).resolve(null)).contains("");
        assertThat(IntegrationTemplate.parse("Header 'h'", "").resolve(null)).contains("");
    }

    @Test
    void givenDollarOrBraceWithoutPlaceholder_whenParse_thenStaticLiteral() {
        IntegrationTemplate template = IntegrationTemplate.parse("Key", "price$5{EUR}");

        assertThat(template.hasPlaceholders()).isFalse();
        assertThat(template.resolve(null)).contains("price$5{EUR}");
    }

    @Test
    void givenLiteral_whenResolve_thenTextVerbatimEvenWithPlaceholderSyntax() {
        IntegrationTemplate template = IntegrationTemplate.literal("${unknown}");

        assertThat(template.hasPlaceholders()).isFalse();
        assertThat(template.resolve(null)).contains("${unknown}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "${foo}          | Key: unknown placeholder '${foo}'",
            "${foo.bar}      | Key: unknown placeholder '${foo.bar}'",
            "${}             | Key: empty placeholder '${}'",
            "${props}        | Key: placeholder '${props}' needs a key, e.g. ${props.KEY}",
            "${metadata.}    | Key: placeholder '${metadata.}' needs a key, e.g. ${metadata.KEY}",
            "abc${clientId   | Key: unclosed placeholder in 'abc${clientId'"
    })
    void givenInvalidTemplate_whenParse_thenThrowsWithLabel(String template, String expectedMessageStart) {
        assertThatThrownBy(() -> IntegrationTemplate.parse("Key", template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(expectedMessageStart);
    }

    @Test
    void givenInvalidTemplate_whenValidate_thenThrowsWithCallerLabel() {
        assertThatThrownBy(() -> IntegrationTemplate.validate("Header 'topic'", "${topic}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Header 'topic': unknown placeholder '${topic}'");
    }

    @Test
    void givenUnknownPlaceholder_whenParse_thenMessageListsAllowedNames() {
        assertThatThrownBy(() -> IntegrationTemplate.parse("Key", "${topic}"))
                .hasMessageContaining("${topicName}")
                .hasMessageContaining("${props.KEY}")
                .hasMessageContaining("${metadata.KEY}");
    }

    @ParameterizedTest
    @CsvSource({"${topic}", "${props}", "${metadata.}"})
    void givenInvalidTemplate_whenParse_thenMessageHasNoAngleBrackets(String template) {
        // The UI shows server errors as HTML, so a '<key>' in the message would be dropped as an unknown tag.
        assertThatThrownBy(() -> IntegrationTemplate.parse("Key", template))
                .message().doesNotContain("<", ">");
    }
}
