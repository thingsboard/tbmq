/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing.shared;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DeviceSharedSubscriptionProcessorImpl.class)
public class DeviceSharedSubscriptionProcessorImplTest {

    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    SharedSubscriptionProcessingStrategyFactory strategyFactory;

    @SpyBean
    DeviceSharedSubscriptionProcessorImpl processor;

    ClientSessionInfo clientSessionInfo;

    @Before
    public void setUp() {
        clientSessionInfo = mock(ClientSessionInfo.class);
    }

    @Test
    public void testConvertToSharedSubscriptionList() {
        Set<Subscription> subscriptions = Set.of(
                newSubscription(0, "group1"),
                newSubscription(1, "group1"),
                newSubscription(2, "group2"),
                newSubscription(0, "group2"),
                newSubscription(1, "group3"),
                newSubscription(2, "group3")
        );
        List<SharedSubscription> sharedSubscriptionList = processor.toSharedSubscriptionList(subscriptions);
        assertEquals(3, sharedSubscriptionList.size());
        sharedSubscriptionList.forEach(sharedSubscription -> assertEquals(2, sharedSubscription.getSubscriptions().size()));
    }

    @Test
    public void testFindAnyConnectedSubscription() {
        List<Subscription> subscriptions = List.of(
                new Subscription("topic1", 1, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic2", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic3", 2, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic4", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic5", 1, ClientSessionInfo.builder().connected(false).build())
        );
        Subscription subscription = processor.findAnyConnectedSubscription(subscriptions);
        assertNull(subscription);

        subscriptions = List.of(
                new Subscription("topic1", 1, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic2", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic3", 2, ClientSessionInfo.builder().connected(true).build()),
                new Subscription("topic4", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic5", 1, ClientSessionInfo.builder().connected(false).build())
        );
        subscription = processor.findAnyConnectedSubscription(subscriptions);
        assertEquals("topic3", subscription.getTopicFilter());
    }

    private Subscription newSubscription(int mqttQoSValue, String shareName) {
        return new Subscription("topic", mqttQoSValue, clientSessionInfo, shareName, SubscriptionOptions.newInstance());
    }
}
