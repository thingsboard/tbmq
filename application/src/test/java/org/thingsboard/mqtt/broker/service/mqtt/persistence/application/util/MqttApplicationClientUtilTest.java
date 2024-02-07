/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class MqttApplicationClientUtilTest {

    @Test
    public void testGetApplicationTopicConstruction() {
        String appTopic = MqttApplicationClientUtil.getAppTopic("12345qwerty", true);
        assertThat(appTopic).isEqualTo("tbmq.msg.app.12345qwerty");

        appTopic = MqttApplicationClientUtil.getAppTopic("12345qwerty", false);
        assertThat(appTopic).isEqualTo("tbmq.msg.app.12345qwerty");

        appTopic = MqttApplicationClientUtil.getAppTopic("12345qwerty)(^", false);
        assertThat(appTopic).isEqualTo("tbmq.msg.app.12345qwerty)(^");

        appTopic = MqttApplicationClientUtil.getAppTopic("12345qwerty)(^", true);
        assertThat(appTopic).isEqualTo("tbmq.msg.app.3712093ccb212b09143030ceaf1b38b38d3cf3da321b216fbe54fb96c4fa4170");
    }

    @Test
    public void testAlphaNumericPattern() {
        Matcher matcher = MqttApplicationClientUtil.ALPHANUMERIC_PATTERN.matcher("123qweasd123");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.ALPHANUMERIC_PATTERN.matcher("asdfg987654ZXCVBN");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.ALPHANUMERIC_PATTERN.matcher("asdaaSFDSFHNV432(0)99");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.ALPHANUMERIC_PATTERN.matcher("zxchgfqwe111^");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.ALPHANUMERIC_PATTERN.matcher("z/xchgfqwe11/1");
        assertThat(matcher.matches()).isFalse();
    }

    @Test
    public void testAlphaNumericSlashPattern() {
        Matcher matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("123qweasd123");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("asdfg987654ZXCVBN");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("asdaaSFDSFHNV432(0)99");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("zxchgfqwe111^");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("zx/chg/fqwe1/11^");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("/zxch/gfqwe111^/");
        assertThat(matcher.matches()).isFalse();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("/123qw/easd123/");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("asdfg987654ZXCVB/N");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("a///sdfg9//87654ZXC/VB/N");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("topic/filter/+/example/#");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("topic/#");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("topic/example/+");
        assertThat(matcher.matches()).isTrue();

        matcher = MqttApplicationClientUtil.SHARED_APP_TOPIC_PATTERN.matcher("topic/example/wrong&/+");
        assertThat(matcher.matches()).isFalse();
    }

    @Test
    public void testGetReplacedSharedAppTopicFilter() {
        String sharedAppTopicFilter = MqttApplicationClientUtil.getReplacedSharedAppTopicFilter("test/123/+");
        assertThat(sharedAppTopicFilter).isEqualTo("tbmq.msg.app.shared.test.123.slw");

        sharedAppTopicFilter = MqttApplicationClientUtil.getReplacedSharedAppTopicFilter("test/123/qwerty");
        assertThat(sharedAppTopicFilter).isEqualTo("tbmq.msg.app.shared.test.123.qwerty");

        sharedAppTopicFilter = MqttApplicationClientUtil.getReplacedSharedAppTopicFilter("one/#");
        assertThat(sharedAppTopicFilter).isEqualTo("tbmq.msg.app.shared.one.mlw");

        sharedAppTopicFilter = MqttApplicationClientUtil.getReplacedSharedAppTopicFilter("two/+/three/#");
        assertThat(sharedAppTopicFilter).isEqualTo("tbmq.msg.app.shared.two.slw.three.mlw");
    }

    @Test
    public void testGetSharedAppTopic() {
        String appSharedTopic = MqttApplicationClientUtil.getSharedAppTopic("12345qwerty/+", true);
        assertThat(appSharedTopic).isEqualTo("tbmq.msg.app.shared.12345qwerty.slw");

        appSharedTopic = MqttApplicationClientUtil.getSharedAppTopic("12345qwerty/#", false);
        assertThat(appSharedTopic).isEqualTo("tbmq.msg.app.shared.12345qwerty.mlw");

        appSharedTopic = MqttApplicationClientUtil.getSharedAppTopic("12345qwerty)(^/+/one", false);
        assertThat(appSharedTopic).isEqualTo("tbmq.msg.app.shared.12345qwerty)(^.slw.one");

        appSharedTopic = MqttApplicationClientUtil.getSharedAppTopic("12345qwerty)(^/+/one", true);
        assertThat(appSharedTopic).isEqualTo("tbmq.msg.app.shared.74a26c4de2926e1893b750d28851891076bfe438dfee97eccc9e5f09d4a5c33d");
    }
}
