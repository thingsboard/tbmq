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
package org.thingsboard.mqtt.broker.queue.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueUtilTest {

    @Test
    void givenValidConfigString_whenParsed_thenReturnsCorrectConfigMap() {
        String properties = "retention.ms:604800000;segment.bytes:26214400;retention.bytes:1048576000;partitions:12;replication.factor:1";

        Map<String, String> configs = QueueUtil.getConfigs(properties);

        assertThat(configs.size()).isEqualTo(5);
        assertThat(configs.get("partitions")).isEqualTo("12");
        assertThat(configs).containsKey("segment.bytes");
    }

    @Test
    void givenConfigFile_whenParsed_thenReturnsCorrectConfigMap() throws Exception {
        Path path = Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("config.txt")).toURI());
        String fileContent = Files.readString(path);

        Map<String, String> result = QueueUtil.getConfigs(fileContent);

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.get("security.protocol")).isEqualTo("SASL_PLAINTEXT");
        assertThat(result.get("sasl.mechanism")).isEqualTo("OAUTHBEARER");
        assertThat(result.get("sasl.jaas.config")).isEqualTo("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId=\"exampleClient\" clientSecret=\"supersecret\";");
        assertThat(result.get("sasl.oauthbearer.token.endpoint.url")).isEqualTo("http://example.com/realms/Example/protocol/openid-connect/token");
    }

    @Test
    void givenComplexConfigString_whenParsed_thenHandlesEscapedSemicolonCorrectly() throws Exception {
        String fileContent = "security.protocol:SASL_PLAINTEXT;sasl.mechanism:OAUTHBEARER;sasl.jaas.config:org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId=\"exampleClient\" clientSecret=\"supersecret\"\\;;sasl.oauthbearer.token.endpoint.url:http://example.com/realms/Example/protocol/openid-connect/token";

        Map<String, String> result = QueueUtil.getConfigs(fileContent);

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.get("security.protocol")).isEqualTo("SASL_PLAINTEXT");
        assertThat(result.get("sasl.mechanism")).isEqualTo("OAUTHBEARER");
        assertThat(result.get("sasl.jaas.config")).isEqualTo("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId=\"exampleClient\" clientSecret=\"supersecret\";");
        assertThat(result.get("sasl.oauthbearer.token.endpoint.url")).isEqualTo("http://example.com/realms/Example/protocol/openid-connect/token");
    }

    @Test
    void givenSimpleKeyValueString_whenParsed_thenReturnsCorrectKeyValuePairs() {
        String config = "k1:v1;k2:v2;k3:v3";
        Map<String, String> result = QueueUtil.getConfigs(config);

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get("k1")).isEqualTo("v1");
        assertThat(result.get("k2")).isEqualTo("v2");
        assertThat(result.get("k3")).isEqualTo("v3");
    }

    @Test
    void givenConfigWithEscapedSemicolon_whenParsed_thenParsesCorrectly() {
        String config = "k1:v1;k2:v2\\;;k3:v3";
        Map<String, String> result = QueueUtil.getConfigs(config);

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get("k1")).isEqualTo("v1");
        assertThat(result.get("k2")).isEqualTo("v2;");
        assertThat(result.get("k3")).isEqualTo("v3");
    }

    @Test
    void givenConfigWithMultipleEscapedSemicolons_whenParsed_thenParsesCorrectly() {
        String config = "k1:v1\\;;k2:v2\\;v2b;k3:v3";
        Map<String, String> result = QueueUtil.getConfigs(config);

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get("k1")).isEqualTo("v1;");
        assertThat(result.get("k2")).isEqualTo("v2;v2b");
        assertThat(result.get("k3")).isEqualTo("v3");
    }

    @Test
    void givenEmptyConfigString_whenParsed_thenReturnsEmptyMap() {
        String config = "";
        Map<String, String> result = QueueUtil.getConfigs(config);

        assertThat(result.size()).isZero();
    }

    @Test
    void givenConfigWithOnlyEscapedSemicolons_whenParsed_thenHandlesEscapedCharacters() {
        String config = "k1:v1\\;\\;\\;";
        Map<String, String> result = QueueUtil.getConfigs(config);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("k1")).isEqualTo("v1;;;");
    }

    @Test
    void givenConfigWithDelimiterAtStart_whenParsed_thenThrowsIllegalArgumentException() {
        String config = ":k1v1";
        assertThatThrownBy(() -> QueueUtil.getConfigs(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenConfigWithDelimiterAtEnd_whenParsed_thenThrowsIllegalArgumentException() {
        String config = "k1v1:";
        assertThatThrownBy(() -> QueueUtil.getConfigs(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenConfigWithMissingDelimiter_whenParsed_thenThrowsIllegalArgumentException() {
        String config = "k1:v1;k2";
        assertThatThrownBy(() -> QueueUtil.getConfigs(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
