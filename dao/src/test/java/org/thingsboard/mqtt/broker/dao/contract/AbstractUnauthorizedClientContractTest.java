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
package org.thingsboard.mqtt.broker.dao.contract;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public abstract class AbstractUnauthorizedClientContractTest extends AbstractContractTest {

    private static final Set<String> ALL_FIXTURES = Set.of("Sensor-Alpha", "sensor-beta", "gateway-1");

    @Autowired
    protected UnauthorizedClientService unauthorizedClientService;

    protected UnauthorizedClient savedUnauthorizedClient;

    @Before
    public void setUp() throws Exception {
        savedUnauthorizedClient = getUnauthorizedClient();
        unauthorizedClientService.save(savedUnauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() throws Exception {
        unauthorizedClientService.deleteAllUnauthorizedClients();
    }

    @Test
    public void givenUnauthorizedClient_whenSave_thenSuccess() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @Test(expected = DataValidationException.class)
    public void givenUnauthorizedClientWithNoClientId_whenSave_thenFailure() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClient.setClientId(null);
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @Test(expected = DataValidationException.class)
    public void givenUnauthorizedClientWithNoIpAddress_whenSave_thenFailure() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClient.setIpAddress(null);
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @Test(expected = DataValidationException.class)
    public void givenUnauthorizedClientWithNoReason_whenSave_thenFailure() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClient.setReason(null);
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @Test(expected = DataValidationException.class)
    public void givenUnauthorizedClientWithNoTs_whenSave_thenFailure() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClient.setTs(null);
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void givenUnauthorizedClient_whenDelete_thenSuccess() throws Exception {
        UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
        unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
        unauthorizedClientService.deleteUnauthorizedClient(unauthorizedClient.getClientId());
        Optional<UnauthorizedClient> foundClient = unauthorizedClientService.findUnauthorizedClient(unauthorizedClient.getClientId());
        Assert.assertFalse(foundClient.isPresent());
    }

    @Test
    public void givenUnauthorizedClient_whenFindById_thenSuccess() {
        Optional<UnauthorizedClient> foundClient = unauthorizedClientService.findUnauthorizedClient(savedUnauthorizedClient.getClientId());
        Assert.assertTrue(foundClient.isPresent());
        Assert.assertEquals(savedUnauthorizedClient.getClientId(), foundClient.get().getClientId());
    }

    @Test
    public void givenUnauthorizedClient_whenFindWithInvalidId_thenNotFound() {
        Optional<UnauthorizedClient> foundClient = unauthorizedClientService.findUnauthorizedClient(UUID.randomUUID().toString());
        Assert.assertFalse(foundClient.isPresent());
    }

    @Test
    public void givenUnauthorizedClient_whenFindAllClients_thenSuccess() {
        PageLink pageLink = new PageLink(100);
        PageData<UnauthorizedClient> clientsPageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(clientsPageData.getData().isEmpty());
        Assert.assertEquals(1, clientsPageData.getData().size());
        Assert.assertEquals(savedUnauthorizedClient.getClientId(), clientsPageData.getData().get(0).getClientId());
    }

    @Test(expected = IncorrectParameterException.class)
    public void givenUnauthorizedClientQueryWithInvalidPageLink_whenFindAllClients_thenFailure() {
        UnauthorizedClientQuery query = new UnauthorizedClientQuery();
        query.setPageLink(new TimePageLink(0));
        unauthorizedClientService.findUnauthorizedClients(query);
    }

    @Test
    public void givenUnauthorizedClient_whenCleanup_thenSuccess() {
        unauthorizedClientService.cleanupUnauthorizedClients(3600); // Cleaning up clients older than 1 hour
        Optional<UnauthorizedClient> foundClient = unauthorizedClientService.findUnauthorizedClient(savedUnauthorizedClient.getClientId());
        Assert.assertFalse(foundClient.isPresent());
    }

    @Test
    public void givenUnauthorizedClients_whenGetUnauthorizedClients_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        int count = 152;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ListenableFuture<Void> future = unauthorizedClientService.save(getUnauthorizedClient());
            futures.add(future);
        }
        Futures.allAsList(futures).get(5, TimeUnit.SECONDS);

        List<UnauthorizedClient> loadedUnauthorizedClientList = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<UnauthorizedClient> pageData;
        do {
            pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
            loadedUnauthorizedClientList.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Assert.assertEquals(count + 1, loadedUnauthorizedClientList.size()); // +1 since we saved in Before method

        loadedUnauthorizedClientList.forEach(uc ->
                unauthorizedClientService.deleteUnauthorizedClient(uc.getClientId()));

        pageLink = new PageLink(33);
        pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenUnauthorizedClients_whenDeleteAllUnauthorizedClients_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        int count = 152;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ListenableFuture<Void> future = unauthorizedClientService.save(getUnauthorizedClient());
            futures.add(future);
        }
        Futures.allAsList(futures).get(5, TimeUnit.SECONDS);

        PageLink pageLink = new PageLink(23);
        PageData<UnauthorizedClient> pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.getData().isEmpty());

        unauthorizedClientService.deleteAllUnauthorizedClients();

        pageLink = new PageLink(33);
        pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenUnauthorizedClients_whenRemoveUnauthorizedClients_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        int count = 152;
        List<UnauthorizedClient> unauthorizedClientList = new ArrayList<>();

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
            unauthorizedClientList.add(unauthorizedClient);
            ListenableFuture<Void> future = unauthorizedClientService.save(unauthorizedClient);
            futures.add(future);
        }
        Futures.allAsList(futures).get(5, TimeUnit.SECONDS);

        PageLink pageLink = new PageLink(23);
        PageData<UnauthorizedClient> pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.getData().isEmpty());

        futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ListenableFuture<Void> future = unauthorizedClientService.remove(unauthorizedClientList.get(i));
            futures.add(future);
        }
        Futures.allAsList(futures).get(5, TimeUnit.SECONDS);
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());

        pageLink = new PageLink(33);
        pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenUnauthorizedClients_whenFindByQuery_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
            ListenableFuture<Void> future = unauthorizedClientService.save(unauthorizedClient);
            futures.add(future);
        }
        Futures.allAsList(futures).get(5, TimeUnit.SECONDS);

        TimePageLink pageLink = new TimePageLink(23);

        UnauthorizedClientQuery query = UnauthorizedClientQuery.builder().pageLink(pageLink).clientId("aaaaaaaaaaa").build();
        PageData<UnauthorizedClient> pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertTrue(pageData.getData().isEmpty());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).ipAddress("168.").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(15, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).username("").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(15, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).username("abc").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertTrue(pageData.getData().isEmpty());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).reason("Invalid").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(15, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).passwordProvidedList(List.of(false)).build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(15, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).tlsUsedList(List.of(false)).build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(15, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).tlsUsedList(List.of(true)).build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenUnauthorizedClients_whenFindByQueryWithUsername_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());

        UnauthorizedClient unauthorizedClient1 = getUnauthorizedClient();
        unauthorizedClientService.save(unauthorizedClient1).get(5, TimeUnit.SECONDS);

        UnauthorizedClient unauthorizedClient2 = getUnauthorizedClient();
        unauthorizedClient2.setUsername("1username2");
        unauthorizedClientService.save(unauthorizedClient2).get(5, TimeUnit.SECONDS);

        TimePageLink pageLink = new TimePageLink(23);

        UnauthorizedClientQuery query = UnauthorizedClientQuery.builder().pageLink(pageLink).username(null).build();
        PageData<UnauthorizedClient> pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(2, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).username("").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(2, pageData.getData().size());

        query = UnauthorizedClientQuery.builder().pageLink(pageLink).username("username").build();
        pageData = unauthorizedClientService.findUnauthorizedClients(query);
        Assert.assertEquals(1, pageData.getData().size());
    }

    @Test
    public void aSaveOfAnExistingClientIdReplacesIt() throws Exception {
        String clientId = savedUnauthorizedClient.getClientId();
        UnauthorizedClient replacement = client(clientId, "10.1.1.1", "replaced-user", true, true, "Client blocked",
                savedUnauthorizedClient.getTs() + 1000);
        unauthorizedClientService.save(replacement).get(5, TimeUnit.SECONDS);

        Assert.assertEquals(Optional.of(replacement), unauthorizedClientService.findUnauthorizedClient(clientId));
        Assert.assertEquals(1, unauthorizedClientService.findUnauthorizedClients(new PageLink(10)).getTotalElements());
    }

    @Test
    public void eachQueryFilterMatchesAsTheSqlQueryDoes() throws Exception {
        saveFilterFixtures();

        Assert.assertEquals(Set.of("Sensor-Alpha", "sensor-beta"), clientIds(query().clientId("SENSOR")));
        Assert.assertEquals(Set.of(), clientIds(query().clientId("absent")));
        Assert.assertEquals(Set.of("sensor-beta", "gateway-1"), clientIds(query().ipAddress("192.168.")));
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().username(null)));
        // an empty username is no restriction: it matches the clients without one too
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().username("")));
        Assert.assertEquals(Set.of("sensor-beta"), clientIds(query().username("user")));
        Assert.assertEquals(Set.of("gateway-1"), clientIds(query().username("OTHER")));
        Assert.assertEquals(Set.of("Sensor-Alpha", "gateway-1"), clientIds(query().reason("bad")));
        Assert.assertEquals(Set.of("sensor-beta", "gateway-1"), clientIds(query().passwordProvidedList(List.of(true))));
        Assert.assertEquals(Set.of("Sensor-Alpha"), clientIds(query().passwordProvidedList(List.of(false))));
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().passwordProvidedList(List.of(true, false))));
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().passwordProvidedList(List.of())));
        Assert.assertEquals(Set.of("gateway-1"), clientIds(query().tlsUsedList(List.of(true))));
        Assert.assertEquals(Set.of("Sensor-Alpha", "sensor-beta"), clientIds(query().tlsUsedList(List.of(false))));
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().tlsUsedList(List.of())));
        Assert.assertEquals(Set.of("sensor-beta"), clientIds(query().ipAddress("192.168").passwordProvidedList(List.of(true))
                .tlsUsedList(List.of(false))));
        Assert.assertEquals(Set.of("gateway-1"), clientIds(query().pageLink(new TimePageLink(10, 0, "GATE"))));
        Assert.assertEquals(Set.of("Sensor-Alpha"), clientIds(query().pageLink(new TimePageLink(10, 0, "alpha")).reason("BAD")));
    }

    @Test
    public void aQueryKeepsTheTimeRangeOfItsPageLinkBothEndsIncluded() throws Exception {
        long base = saveFilterFixtures();

        Assert.assertEquals(Set.of("sensor-beta", "gateway-1"), clientIds(query().pageLink(timeRange(base + 1000, base + 2000))));
        Assert.assertEquals(Set.of("gateway-1"), clientIds(query().pageLink(timeRange(base + 1001, null))));
        Assert.assertEquals(Set.of("Sensor-Alpha", "sensor-beta"), clientIds(query().pageLink(timeRange(null, base + 1000))));
        Assert.assertEquals(Set.of("Sensor-Alpha"), clientIds(query().pageLink(timeRange(base, base))));
        Assert.assertEquals(Set.of(), clientIds(query().pageLink(timeRange(base + 2001, null))));
        Assert.assertEquals(ALL_FIXTURES, clientIds(query().pageLink(timeRange(null, null))));
    }

    @Test
    public void aQueryIsSortedAndPagedByItsPageLink() throws Exception {
        saveFilterFixtures();

        TimePageLink firstPage = new TimePageLink(2, 0, null, new SortOrder("ts", SortOrder.Direction.DESC));
        PageData<UnauthorizedClient> first = unauthorizedClientService.findUnauthorizedClients(query().pageLink(firstPage).build());
        PageData<UnauthorizedClient> second = unauthorizedClientService.findUnauthorizedClients(query().pageLink(firstPage.nextPageLink()).build());

        Assert.assertEquals(List.of("gateway-1", "sensor-beta"), first.getData().stream().map(UnauthorizedClient::getClientId).toList());
        Assert.assertEquals(3, first.getTotalElements());
        Assert.assertEquals(2, first.getTotalPages());
        Assert.assertTrue(first.hasNext());
        Assert.assertEquals(List.of("Sensor-Alpha"), second.getData().stream().map(UnauthorizedClient::getClientId).toList());
        Assert.assertFalse(second.hasNext());
    }

    @Test
    public void aPageLinkTextSearchMatchesThePartOfAClientIdIgnoringCase() throws Exception {
        saveFilterFixtures();

        PageData<UnauthorizedClient> page = unauthorizedClientService.findUnauthorizedClients(
                new PageLink(10, 0, "ALPHA", new SortOrder("clientId", SortOrder.Direction.ASC)));

        Assert.assertEquals(List.of("Sensor-Alpha"), page.getData().stream().map(UnauthorizedClient::getClientId).toList());
        Assert.assertEquals(3, unauthorizedClientService.findUnauthorizedClients(new PageLink(10, 0, "")).getTotalElements());
    }

    @Test
    public void aCleanupRemovesOnlyTheClientsOlderThanTheTtl() throws Exception {
        UnauthorizedClient fresh = getUnauthorizedClient();
        fresh.setTs(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));
        unauthorizedClientService.save(fresh).get(5, TimeUnit.SECONDS);

        unauthorizedClientService.cleanupUnauthorizedClients(TimeUnit.HOURS.toSeconds(1));

        Assert.assertFalse(unauthorizedClientService.findUnauthorizedClient(savedUnauthorizedClient.getClientId()).isPresent());
        Assert.assertTrue(unauthorizedClientService.findUnauthorizedClient(fresh.getClientId()).isPresent());

        unauthorizedClientService.cleanupUnauthorizedClients(TimeUnit.HOURS.toSeconds(3));

        Assert.assertTrue(unauthorizedClientService.findUnauthorizedClient(fresh.getClientId()).isPresent());
    }

    /**
     * Saves three clients a second apart in place of the client saved by {@link #setUp()}.
     *
     * @return the ts of the first one
     */
    private long saveFilterFixtures() throws Exception {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());
        long base = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
        Futures.allAsList(
                unauthorizedClientService.save(client("Sensor-Alpha", "10.0.0.1", null, false, false, "Bad credentials", base)),
                unauthorizedClientService.save(client("sensor-beta", "192.168.1.20", "Device-User", true, false, "Client blocked", base + 1000)),
                unauthorizedClientService.save(client("gateway-1", "192.168.1.30", "other", true, true, "Bad credentials", base + 2000))
        ).get(5, TimeUnit.SECONDS);
        return base;
    }

    private static UnauthorizedClientQuery.UnauthorizedClientQueryBuilder query() {
        return UnauthorizedClientQuery.builder().pageLink(new TimePageLink(10));
    }

    private static TimePageLink timeRange(Long startTime, Long endTime) {
        return new TimePageLink(10, 0, null, null, startTime, endTime);
    }

    private Set<String> clientIds(UnauthorizedClientQuery.UnauthorizedClientQueryBuilder query) {
        PageData<UnauthorizedClient> page = unauthorizedClientService.findUnauthorizedClients(query.build());
        Assert.assertEquals(page.getData().size(), page.getTotalElements());
        return page.getData().stream().map(UnauthorizedClient::getClientId).collect(Collectors.toSet());
    }

    private static UnauthorizedClient client(String clientId, String ipAddress, String username, boolean passwordProvided,
                                             boolean tlsUsed, String reason, long ts) {
        return UnauthorizedClient.builder().clientId(clientId).ipAddress(ipAddress).username(username)
                .passwordProvided(passwordProvided).tlsUsed(tlsUsed).reason(reason).ts(ts).build();
    }

    private UnauthorizedClient getUnauthorizedClient() {
        UnauthorizedClient client = new UnauthorizedClient();
        client.setClientId(UUID.randomUUID().toString());
        client.setIpAddress("192.168.0.1");
        client.setReason("Invalid credentials");
        client.setTs(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));
        return client;
    }

}
