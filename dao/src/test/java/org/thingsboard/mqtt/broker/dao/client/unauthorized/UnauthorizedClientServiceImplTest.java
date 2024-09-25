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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@DaoSqlTest
public class UnauthorizedClientServiceImplTest extends AbstractServiceTest {

    @Autowired
    private UnauthorizedClientService unauthorizedClientService;

    UnauthorizedClient savedUnauthorizedClient;

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
        for (int i = 0; i < count; i++) {
            unauthorizedClientService.save(getUnauthorizedClient()).get(5, TimeUnit.SECONDS);
        }

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
        for (int i = 0; i < count; i++) {
            unauthorizedClientService.save(getUnauthorizedClient()).get(5, TimeUnit.SECONDS);
        }

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
        for (int i = 0; i < count; i++) {
            UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
            unauthorizedClientList.add(unauthorizedClient);
            unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
        }

        PageLink pageLink = new PageLink(23);
        PageData<UnauthorizedClient> pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.getData().isEmpty());

        for (int i = 0; i < count; i++) {
            unauthorizedClientService.remove(unauthorizedClientList.get(i)).get(5, TimeUnit.SECONDS);
        }
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());

        pageLink = new PageLink(33);
        pageData = unauthorizedClientService.findUnauthorizedClients(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenUnauthorizedClients_whenFindByQuery_thenSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());

        for (int i = 0; i < 15; i++) {
            UnauthorizedClient unauthorizedClient = getUnauthorizedClient();
            unauthorizedClientService.save(unauthorizedClient).get(5, TimeUnit.SECONDS);
        }

        TimePageLink pageLink = new TimePageLink(23);

        UnauthorizedClientQuery query = UnauthorizedClientQuery.builder().pageLink(pageLink).clientId("aaa").build();
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

    @NotNull
    private UnauthorizedClient getUnauthorizedClient() {
        UnauthorizedClient client = new UnauthorizedClient();
        client.setClientId(UUID.randomUUID().toString());
        client.setIpAddress("192.168.0.1");
        client.setReason("Invalid credentials");
        client.setTs(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));
        return client;
    }

}
