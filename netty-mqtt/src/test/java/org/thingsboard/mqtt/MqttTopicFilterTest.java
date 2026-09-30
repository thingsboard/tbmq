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
package org.thingsboard.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MqttTopicFilterTest {

    @ParameterizedTest(name = "[{index}] filter {0} matches {1}")
    @CsvSource({
            // exact
            "sensors/a,        sensors/a",
            "sensors,          sensors",
            // single-level wildcard
            "sensors/+,        sensors/a",
            "sensors/+/t,      sensors/a/t",
            "+,                a",
            "sensors/+,        'sensors/'",          // '+' matches an empty level
            // multi-level wildcard, including the parent level (MQTT 3.1.1 4.7.1.2)
            "sensors/#,        sensors",
            "sensors/#,        sensors/a",
            "sensors/#,        sensors/a/b/c",
            "#,                a",
            "#,                a/b",
            // shared subscriptions: the broker delivers the real topic, not the share filter
            "$share/g1/sensors/#,   sensors/a",
            "$share/g1/sensors/+,   sensors/a",
            "$share/group-2/a/b,    a/b",
            // a leading '/' is a distinct empty first level, not a no-op (MQTT 3.1.1 4.7.1.1)
            "/finance,         /finance",
    })
    void filtersThatMustMatch(String filter, String topic) {
        assertThat(MqttTopicFilter.of(filter).matches(topic, MqttTopicFilter.split(topic)))
                .as("%s should match %s", filter, topic)
                .isTrue();
    }

    @ParameterizedTest(name = "[{index}] filter {0} does not match {1}")
    @CsvSource({
            // regex metacharacters in the filter are literal, not live
            "v1.0/x,           v1a0/x",
            "a+b/x,            aab/x",
            "a*/x,             aaa/x",
            "a(b)/x,           ab/x",
            // wildcards do not cross levels
            "sensors/+,        sensors/a/b",
            "sensors/+,        sensors",
            "sensors/a,        sensors/a/b",
            "sensors/a/b,      sensors/a",
            // a leading wildcard never matches a $-topic (MQTT 3.1.1 4.7.2)
            "#,                $SYS/uptime",
            "+/uptime,         $SYS/uptime",
            "+,                $SYS",
            // malformed shared subscriptions can never match
            "$share//sensors,  sensors",
            "$share/g1/,       sensors",
            "$share/g1,        sensors",
            // '#' must be the last level of a filter (MQTT 3.1.1 4.7.1.2); a filter that breaks this can never match
            "#/a,              anything/at/all",
            "a/#/b,            a/zzz",
            "'#/',             a/zzz",
            // a leading '/' is a distinct empty first level, not a no-op (MQTT 3.1.1 4.7.1.1)
            "/finance,         finance",
    })
    void filtersThatMustNotMatch(String filter, String topic) {
        assertThat(MqttTopicFilter.of(filter).matches(topic, MqttTopicFilter.split(topic)))
                .as("%s should not match %s", filter, topic)
                .isFalse();
    }

    @Test
    void anExplicitDollarFilterStillMatchesItsDollarTopic() {
        // only a *wildcard* first level is barred from $-topics; a literal one is fine
        assertThat(MqttTopicFilter.of("$SYS/#").matches("$SYS/uptime", MqttTopicFilter.split("$SYS/uptime")))
                .isTrue();
    }

    @Test
    void splitKeepsTrailingEmptyLevels() {
        assertThat(MqttTopicFilter.split("a/b/")).containsExactly("a", "b", "");
    }
}
