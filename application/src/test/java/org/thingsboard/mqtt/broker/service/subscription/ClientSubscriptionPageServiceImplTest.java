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
package org.thingsboard.mqtt.broker.service.subscription;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientSubscriptionQuery;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.ClientSubscriptionInfoDto;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientSubscriptionPageServiceImplTest {

    ClientSubscriptionCache clientSubscriptionCache;
    ClientSubscriptionPageServiceImpl clientSubscriptionPageService;

    @Before
    public void setUp() {
        clientSubscriptionCache = mock(ClientSubscriptionCache.class);
        clientSubscriptionPageService = spy(new ClientSubscriptionPageServiceImpl(clientSubscriptionCache));
    }

    @Test
    public void givenNoSubscriptions_whenGetClientSubscriptions_thenGetNothing() {
        ClientSubscriptionQuery query = new ClientSubscriptionQuery();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(Collections.emptyMap());

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertTrue(CollectionUtils.isEmpty(result.getData()));
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertFalse(result.hasNext());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLink_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(2, 0))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertFalse(result.getData().isEmpty());
        assertEquals(2, result.getData().size());
        assertEquals(3, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndTextSearch_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0, "abc"))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertFalse(result.getData().isEmpty());
        assertEquals(1, result.getData().size());
        assertEquals("abcdf", result.getData().get(0).getClientId());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndClientId_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .clientId("abc")
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertFalse(result.getData().isEmpty());
        assertEquals(1, result.getData().size());
        assertEquals("abcdf", result.getData().get(0).getClientId());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndTopicFilter_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .topicFilter("2")
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(1, result.getData().size());
        assertEquals("client2", result.getData().get(0).getClientId());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndQosSet_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .qosSet(Set.of(1))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(1, result.getData().size());
        assertEquals("client1", result.getData().get(0).getClientId());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndNoLocalList_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .noLocalList(List.of(false))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(3, result.getData().size());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndRetainAsPubList_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .retainAsPublishList(List.of(true))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(0, result.getData().size());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndWrongRetainHandlingSet_thenGetNothing() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .retainHandlingSet(Set.of(2, 3))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(0, result.getData().size());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndRetainHandlingSet_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .retainHandlingSet(Set.of(0, 1, 2))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(3, result.getData().size());
    }

    @Test
    public void givenSubscriptions_whenGetClientSubscriptionsWithPageLinkAndSubscriptionId_thenGetExpectedResult() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(10, 0))
                .subscriptionId(1)
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);

        assertEquals(2, result.getData().size());
    }

    @Test
    public void givenManySubscriptions_whenGetClientSubscriptions_thenGetExpectedResultFastEnough() {
        ClientSubscriptionQuery query = ClientSubscriptionQuery
                .builder()
                .pageLink(new PageLink(30, 0))
                .retainHandlingSet(Set.of(1))
                .build();

        Map<String, Set<TopicSubscription>> allSubscriptions = getRandomSubscriptionsMap();
        when(clientSubscriptionCache.getAllClientSubscriptions()).thenReturn(allSubscriptions);

        long start = System.nanoTime();
        PageData<ClientSubscriptionInfoDto> result = clientSubscriptionPageService.getClientSubscriptions(query);
        long end = System.nanoTime();

        System.out.println("Took " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms to process getClientSubscriptions by query");

        assertEquals(1, result.getData().size());
    }

    private Map<String, Set<TopicSubscription>> getRandomSubscriptionsMap() {
        Map<String, Set<TopicSubscription>> subscriptions = new HashMap<>();
        Random random = new Random();

        for (int i = 0; i < 1_000_000; i++) {
            int qos = random.nextInt(3);

            subscriptions.put(RandomStringUtils.randomAlphabetic(10),
                    Set.of(
                            new TopicSubscription(RandomStringUtils.randomAlphabetic(10), qos, null, SubscriptionOptions.newInstance(), -1),
                            new TopicSubscription(RandomStringUtils.randomAlphabetic(10), qos, null, SubscriptionOptions.newInstance(), -1),
                            new TopicSubscription(RandomStringUtils.randomAlphabetic(10), qos, null, SubscriptionOptions.newInstance(), -1),
                            new TopicSubscription(RandomStringUtils.randomAlphabetic(10), qos, null, SubscriptionOptions.newInstance(), -1),
                            new TopicSubscription(RandomStringUtils.randomAlphabetic(10), qos, null, SubscriptionOptions.newInstance(), -1)
                    )
            );
        }

        subscriptions.put("abc",
                Set.of(
                        new TopicSubscription(
                                "test/topic",
                                1,
                                "shared",
                                new SubscriptionOptions(
                                        true,
                                        true,
                                        SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS),
                                10)
                )
        );

        return subscriptions;
    }

    private Map<String, Set<TopicSubscription>> getSubscriptionsMap() {
        Map<String, Set<TopicSubscription>> subscriptions = new HashMap<>();

        subscriptions.put("client1", Set.of(new TopicSubscription("tf1", 1, "sn", SubscriptionOptions.newInstance(), -1)));
        subscriptions.put("client2", Set.of(new TopicSubscription("tf2", 2, "shared", SubscriptionOptions.newInstance(), 1)));
        subscriptions.put("abcdf", Set.of(new TopicSubscription("test/test", 0, null, SubscriptionOptions.newInstance(), 1)));

        return subscriptions;
    }

}
