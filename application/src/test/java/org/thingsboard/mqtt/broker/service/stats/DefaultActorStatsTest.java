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
package org.thingsboard.mqtt.broker.service.stats;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.shared.TimedMsg;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultActorStatsTest {

    private static final String DEVICE_MESSAGES_PACKAGE = "org.thingsboard.mqtt.broker.actors.device.messages";

    private SimpleMeterRegistry meterRegistry;
    private StatsFactory statsFactory;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        statsFactory = new DefaultStatsFactory(meterRegistry);
    }

    static Stream<Arguments> actorTypes() {
        return Stream.of(
                Arguments.of(StatsType.CLIENT_ACTOR, "clientActor.processing.time"),
                Arguments.of(StatsType.PERSISTED_DEVICE_ACTOR, "deviceActor.processing.time")
        );
    }

    @ParameterizedTest
    @MethodSource("actorTypes")
    public void givenActorType_whenLogProcessingTime_thenTimerNamedByType(StatsType statsType, String expectedTimerName) {
        ActorStats stats = new DefaultActorStats(statsFactory, statsType);
        stats.logMsgProcessingTime(MsgType.INCOMING_PUBLISH_MSG, System.nanoTime());

        Timer timer = meterRegistry.find(expectedTimerName)
                .tag(StatsConstantNames.MSG_TYPE, MsgType.INCOMING_PUBLISH_MSG.toString()).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    public void givenTimedMsg_whenLogQueueTime_thenQueueTimerRecords() {
        ActorStats stats = new DefaultActorStats(statsFactory, StatsType.PERSISTED_DEVICE_ACTOR);
        stats.logMsgQueueTime(new TestTimedMsg(System.nanoTime() - 2_000_000L));

        Timer timer = meterRegistry.find("deviceActor.msgInQueueTime").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(stats.getMsgCount()).isEqualTo(1);
    }

    // Enforces the invariant that every device actor message is a TimedMsg, so queue-wait time is
    // recorded for all of them. Scanning the package (rather than spot-checking a few classes) means a
    // newly added device message that forgets to extend AbstractTimedMsg fails this test instead of
    // silently dropping its queue-time metric.
    @Test
    public void allDeviceMessagesAreTimedMsg() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(TbActorMsg.class));

        Set<BeanDefinition> deviceMessages = scanner.findCandidateComponents(DEVICE_MESSAGES_PACKAGE);
        assertThat(deviceMessages)
                .as("expected to discover the device actor message classes in %s", DEVICE_MESSAGES_PACKAGE)
                .isNotEmpty();

        for (BeanDefinition beanDefinition : deviceMessages) {
            Class<?> messageClass = Class.forName(beanDefinition.getBeanClassName());
            assertThat(TimedMsg.class)
                    .as("%s must be a TimedMsg so its mailbox queue-wait time is recorded", messageClass.getName())
                    .isAssignableFrom(messageClass);
        }
    }

    // Minimal TimedMsg stand-in for the queue-timer unit test.
    private record TestTimedMsg(long createdTimeNanos) implements TimedMsg {
        @Override
        public long getMsgCreatedTimeNanos() {
            return createdTimeNanos;
        }
    }
}
