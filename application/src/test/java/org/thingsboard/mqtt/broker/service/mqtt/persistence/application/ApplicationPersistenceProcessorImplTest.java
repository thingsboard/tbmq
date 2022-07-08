package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ApplicationPersistenceProcessorImpl.class)
@TestPropertySource(properties = {
        "queue.application-persisted-msg.poll-interval=100",
        "queue.application-persisted-msg.pack-processing-timeout=2000"
})
@Slf4j
public class ApplicationPersistenceProcessorImplTest {

    @MockBean
    ApplicationMsgAcknowledgeStrategyFactory acknowledgeStrategyFactory;
    @MockBean
    ApplicationSubmitStrategyFactory submitStrategyFactory;
    @MockBean
    ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    @MockBean
    PublishMsgDeliveryService publishMsgDeliveryService;
    @MockBean
    TbQueueAdmin queueAdmin;
    @MockBean
    StatsManager statsManager;
    @MockBean
    ApplicationPersistedMsgCtxService unacknowledgedPersistedMsgCtxService;
    @MockBean
    ClientMqttActorManager clientMqttActorManager;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    ApplicationTopicService applicationTopicService;

    @SpyBean
    ApplicationPersistenceProcessorImpl applicationPersistenceProcessor;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void test() {

    }
}