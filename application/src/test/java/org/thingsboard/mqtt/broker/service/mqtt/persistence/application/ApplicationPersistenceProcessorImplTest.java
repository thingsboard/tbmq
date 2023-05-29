/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import com.google.common.util.concurrent.Futures;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
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
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ApplicationPersistenceProcessorImpl.class)
@TestPropertySource(properties = {
        "queue.application-persisted-msg.poll-interval=100",
        "queue.application-persisted-msg.pack-processing-timeout=2000",
        "queue.application-persisted-msg.threads-count=1"
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
    public void testCollectCancelledJobs() {
        Set<TopicSharedSubscription> subscriptionTopicFilters = Set.of(
                newSharedSubscriptionTopicFilter("test/1"),
                newSharedSubscriptionTopicFilter("test/2"),
                newSharedSubscriptionTopicFilter("test/3")
        );
        List<ApplicationSharedSubscriptionJob> jobs = new ArrayList<>(Arrays.asList(
                newApplicationSharedSubscriptionJob("test/1"),
                newApplicationSharedSubscriptionJob("test/2"),
                newApplicationSharedSubscriptionJob("test/3"),
                newApplicationSharedSubscriptionJob("test/4"),
                newApplicationSharedSubscriptionJob("test/5")
        ));
        List<ApplicationSharedSubscriptionJob> cancelledJobs =
                applicationPersistenceProcessor.collectCancelledJobs(subscriptionTopicFilters, "client", jobs);

        Assert.assertEquals(3, cancelledJobs.size());
        cancelledJobs.forEach(job -> Assert.assertTrue(job.isInterrupted()));

        List<TopicSharedSubscription> cancelledSubscriptions = cancelledJobs.stream()
                .map(ApplicationSharedSubscriptionJob::getSubscription)
                .collect(Collectors.toList());
        Assert.assertTrue(cancelledSubscriptions.containsAll(
                List.of(newSharedSubscriptionTopicFilter("test/1"),
                        newSharedSubscriptionTopicFilter("test/2"),
                        newSharedSubscriptionTopicFilter("test/3"))
        ));

        jobs.removeAll(cancelledJobs);
        jobs.forEach(job -> Assert.assertFalse(job.isInterrupted()));
        Assert.assertEquals(2, jobs.size());
    }

    private ApplicationSharedSubscriptionJob newApplicationSharedSubscriptionJob(String topicFilter) {
        return new ApplicationSharedSubscriptionJob(newSharedSubscriptionTopicFilter(topicFilter), Futures.immediateFuture(null), false);
    }

    private TopicSharedSubscription newSharedSubscriptionTopicFilter(String topicFilter) {
        return new TopicSharedSubscription(topicFilter, null);
    }
}