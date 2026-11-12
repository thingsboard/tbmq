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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class StandaloneDownLinkQueuePublisherTest {

    private final TbMessageStatsReportClient statsReportClient = mock(TbMessageStatsReportClient.class);
    private final StandaloneDownLinkQueuePublisher publisher = new StandaloneDownLinkQueuePublisher(statsReportClient);

    private final Logger logger = (Logger) LoggerFactory.getLogger(StandaloneDownLinkQueuePublisher.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void publishBasicMsgCountsOneDroppedMessageAndLogsAnError() {
        publisher.publishBasicMsg("remote-node", "client-1", PublishMsgProto.getDefaultInstance());

        verify(statsReportClient).reportDroppedMsgs();
        verifyNoMoreInteractions(statsReportClient);
        assertSingleErrorMentions("client-1", "remote-node");
    }

    @Test
    void publishPersistentMsgCountsOneDroppedMessageAndLogsAnError() {
        publisher.publishPersistentMsg("remote-node", "client-2", mock(DevicePublishMsg.class));

        verify(statsReportClient).reportDroppedMsgs();
        verifyNoMoreInteractions(statsReportClient);
        assertSingleErrorMentions("client-2", "remote-node");
    }

    @Test
    void eachCallCountsOneDroppedMessage() {
        publisher.publishBasicMsg("remote-node", "client-1", PublishMsgProto.getDefaultInstance());
        publisher.publishPersistentMsg("remote-node", "client-1", mock(DevicePublishMsg.class));
        publisher.publishBasicMsg("remote-node", "client-3", PublishMsgProto.getDefaultInstance());

        verify(statsReportClient, times(3)).reportDroppedMsgs();
        verifyNoMoreInteractions(statsReportClient);
    }

    @Test
    void isStandaloneOnly() {
        assertThat(StandaloneDownLinkQueuePublisher.class.isAnnotationPresent(StandaloneComponent.class)).isTrue();
    }

    private void assertSingleErrorMentions(String clientId, String serviceId) {
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("remote delivery requested on a single-node broker")
                .contains(clientId)
                .contains(serviceId);
    }

}
