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
package org.thingsboard.mqtt.broker.dao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@DaoSqlTest
public class MqttClientCredentialsServiceTest extends AbstractServiceTest {

    private final IdComparator<ShortMqttClientCredentials> idComparator = new IdComparator<>();

    @Autowired
    private MqttClientCredentialsService mqttClientCredentialsService;
    @Autowired
    private CacheNameResolver cacheNameResolver;

    Cache mqttClientCredentialsCache;
    Cache basicCredentialsPasswordCache;
    Cache sslRegexBasedCredentialsCache;

    @Before
    public void setUp() {
        mqttClientCredentialsCache = cacheNameResolver.getCache(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
        basicCredentialsPasswordCache = cacheNameResolver.getCache(CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE);
        sslRegexBasedCredentialsCache = cacheNameResolver.getCache(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE);
    }

    @After
    public void tearDown() throws Exception {
        PageData<ShortMqttClientCredentials> credentials = mqttClientCredentialsService.getCredentials(new PageLink(1000));
        for (var credential : credentials.getData()) {
            mqttClientCredentialsService.deleteCredentials(credential.getId());
        }
    }

    private void checkCacheNonNullAndEvict(String credentialsId) {
        Objects.requireNonNull(mqttClientCredentialsCache, "Cache manager is null").evict(credentialsId);
    }

    @Test
    public void testSaveCredentialsCheckCache() throws JsonProcessingException {
        String credentialsId = ProtocolUtil.clientIdCredentialsId("cacheClient");

        checkCacheNonNullAndEvict(credentialsId);
        mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("cachedName", "cacheClient", null, null)
        );

        List<MqttClientCredentials> credentials = mqttClientCredentialsService.findMatchingCredentials(List.of(credentialsId));
        Assert.assertNotNull(credentials);
        Assert.assertFalse(credentials.isEmpty());
        MqttClientCredentials clientCredentials = mqttClientCredentialsCache.get(credentialsId, MqttClientCredentials.class);
        Assert.assertNotNull(clientCredentials);
        Assert.assertEquals(clientCredentials, credentials.get(0));

        mqttClientCredentialsService.deleteCredentials(credentials.get(0).getId());
        Assert.assertNull(mqttClientCredentialsCache.get(credentialsId, MqttClientCredentials.class));
    }

    @Test
    public void testSaveCredentialsCheckCacheAfterAnotherSave() throws JsonProcessingException {
        String credentialsId = ProtocolUtil.clientIdCredentialsId("cacheClient");

        checkCacheNonNullAndEvict(credentialsId);
        mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("cachedName", "cacheClient", null, null)
        );

        List<MqttClientCredentials> credentials = mqttClientCredentialsService.findMatchingCredentials(List.of(credentialsId));
        Assert.assertNotNull(credentials);
        Assert.assertFalse(credentials.isEmpty());
        Assert.assertNotNull(mqttClientCredentialsCache.get(credentialsId, MqttClientCredentials.class));

        MqttClientCredentials clientCredentials = credentials.get(0);
        clientCredentials.setName("newName");
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(clientCredentials);
        Assert.assertNull(mqttClientCredentialsCache.get(credentialsId, MqttClientCredentials.class));

        mqttClientCredentialsService.deleteCredentials(savedCredentials.getId());
    }

    @Test
    public void testSaveTwoCredentialsCheckCacheAfterFetchOneOfThem() throws JsonProcessingException {
        String credentialsId1 = ProtocolUtil.clientIdCredentialsId("cacheClient1");
        String credentialsId2 = ProtocolUtil.clientIdCredentialsId("cacheClient2");

        checkCacheNonNullAndEvict(credentialsId1);
        checkCacheNonNullAndEvict(credentialsId2);
        mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("cachedName1", "cacheClient1", null, null)
        );
        mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("cachedName2", "cacheClient2", null, null)
        );

        List<MqttClientCredentials> credentials = mqttClientCredentialsService.findMatchingCredentials(List.of(credentialsId1));
        Assert.assertNotNull(credentials);
        Assert.assertFalse(credentials.isEmpty());
        Assert.assertNotNull(mqttClientCredentialsCache.get(credentialsId1, MqttClientCredentials.class));
        Assert.assertNull(mqttClientCredentialsCache.get(credentialsId2, MqttClientCredentials.class));

        credentials = mqttClientCredentialsService.findMatchingCredentials(List.of(credentialsId1, credentialsId2));
        Assert.assertNotNull(mqttClientCredentialsCache.get(credentialsId2, MqttClientCredentials.class));

        mqttClientCredentialsService.deleteCredentials(credentials.get(0).getId());
        mqttClientCredentialsService.deleteCredentials(credentials.get(1).getId());
    }

    @Test
    public void testCreateValidCredentials() throws JsonProcessingException {
        mqttClientCredentialsService.saveCredentials(validMqttClientCredentials("test", "client", "user", null));
    }

    @Test
    public void testCreateValidScramCredentials() throws Exception {
        mqttClientCredentialsService.saveCredentials(validScramMqttClientCredentials());
    }

    @Test(expected = DataValidationException.class)
    public void testCreateCredentialsWithSystemWebSocketCredentialsName() throws JsonProcessingException {
        mqttClientCredentialsService.saveCredentials(validMqttClientCredentials(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME, "client", "user", null));
    }

    @Test(expected = DataValidationException.class)
    public void testUpdateSystemWebSocketCredentials() throws JsonProcessingException {
        MqttClientCredentials systemMqttClientCredentials = mqttClientCredentialsService.saveSystemWebSocketCredentials();

        MqttClientCredentials mqttClientCredentials = validMqttClientCredentials("anotherName", "client", "user", "password");
        mqttClientCredentials.setId(systemMqttClientCredentials.getId());

        mqttClientCredentialsService.saveCredentials(mqttClientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateCredentialsWithEmptyName() throws JsonProcessingException {
        mqttClientCredentialsService.saveCredentials(validMqttClientCredentials(null, "client", "user", null));
    }

    @Test(expected = DataValidationException.class)
    public void testCreateDeviceCredentialsWithEmptyCredentialsType() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName("TestClient");
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateNoClientAndUsername() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateNotValidCredentialsValue() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName("TestClient");
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        clientCredentials.setCredentialsValue("NOT_VALID");
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateNotValidCredentialsValue_WrongAuthPattern() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName("TestClient");
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials wrongPatternBasicCred = BasicMqttCredentials.newInstance("test", "test", "test", List.of("(not_closed"));
        clientCredentials.setCredentialsValue(JacksonUtil.toString(wrongPatternBasicCred));
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }


    @Test(expected = DataValidationException.class)
    public void testCreateDuplicateCredentials() throws JsonProcessingException {
        MqttClientCredentials clientCredentials = mqttClientCredentialsService.saveCredentials(validMqttClientCredentials("test", "client", "user", null));
        try {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentials("test", "client", "user", "password"));
        } finally {
            mqttClientCredentialsService.deleteCredentials(clientCredentials.getId());
        }
    }

    @Test
    public void testFindMatchingMixed() throws JsonProcessingException {
        MqttClientCredentials client1Credentials, client2Credentials, client3Credentials;
        client1Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", "client1", "test1", "password1"));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", "client2", "test1", null));
        client3Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", "client1", "test2", null));

        Assert.assertEquals(
                Collections.singletonList(client1Credentials),
                mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                        ProtocolUtil.mixedCredentialsId("test1", "client1")
                )));

        mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
        mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
        mqttClientCredentialsService.deleteCredentials(client3Credentials.getId());
    }

    @Test
    public void testFindMatchingByUserName() throws JsonProcessingException {
        MqttClientCredentials client1Credentials, client2Credentials;
        client1Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", null, "user1", null));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", null, "user2", null));

        Assert.assertEquals(
                Collections.singletonList(client1Credentials),
                mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                        ProtocolUtil.usernameCredentialsId("user1")
                )));

        mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
        mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
    }

    @Test
    public void testFindMatchingByClientId() throws JsonProcessingException {
        MqttClientCredentials client1Credentials, client2Credentials;
        client1Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", "client1", null, null));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test", "client2", null, null));

        Assert.assertEquals(
                Collections.singletonList(client1Credentials),
                mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                        ProtocolUtil.clientIdCredentialsId("client1")
                )));

        mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
        mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
    }

    @Test
    public void testFindMqttClientCredentials() throws JsonProcessingException {
        List<ShortMqttClientCredentials> mqttClientCredentialsList = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials(
                            "Credentials" + i,
                            "clientId" + i,
                            "username" + i,
                            "password" + i));
            mqttClientCredentialsList.add(MqttClientCredentialsUtil.toShortMqttClientCredentials(savedCredentials));
        }

        List<ShortMqttClientCredentials> loadedMqttClientCredentialsList = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<ShortMqttClientCredentials> pageData;
        do {
            pageData = mqttClientCredentialsService.getCredentials(pageLink);
            loadedMqttClientCredentialsList.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        mqttClientCredentialsList.sort(idComparator);
        loadedMqttClientCredentialsList.sort(idComparator);

        Assert.assertEquals(mqttClientCredentialsList, loadedMqttClientCredentialsList);

        loadedMqttClientCredentialsList.forEach(smcc ->
                mqttClientCredentialsService.deleteCredentials(smcc.getId()));

        pageLink = new PageLink(33);
        pageData = mqttClientCredentialsService.getCredentials(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void testFindMqttClientCredentialsByQueryWithEmptyLists() throws JsonProcessingException {
        List<ShortMqttClientCredentials> mqttClientCredentialsList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials(
                            "Credentials" + i,
                            "clientId" + i,
                            "username" + i,
                            "password" + i));
            mqttClientCredentialsList.add(MqttClientCredentialsUtil.toShortMqttClientCredentials(savedCredentials));
        }

        List<ShortMqttClientCredentials> loadedMqttClientCredentialsList = new ArrayList<>();
        PageLink pageLink = new PageLink(13);
        ClientCredentialsQuery query = new ClientCredentialsQuery(pageLink, List.of(), null);

        PageData<ShortMqttClientCredentials> pageData;
        do {
            pageData = mqttClientCredentialsService.getCredentialsV2(query);
            loadedMqttClientCredentialsList.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        mqttClientCredentialsList.sort(idComparator);
        loadedMqttClientCredentialsList.sort(idComparator);

        Assert.assertEquals(mqttClientCredentialsList, loadedMqttClientCredentialsList);

        loadedMqttClientCredentialsList.forEach(smcc ->
                mqttClientCredentialsService.deleteCredentials(smcc.getId()));

        pageLink = new PageLink(33);
        query.setPageLink(pageLink);
        pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void testFindMqttClientCredentialsByQuery() throws JsonProcessingException {
        for (int i = 0; i < 5; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        }
        for (int i = 0; i < 4; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        }
        for (int i = 0; i < 3; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        }
        for (int i = 0; i < 2; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        }

        findByQueryAndVerifyResult(List.of(ClientType.DEVICE), List.of(ClientCredentialsType.MQTT_BASIC), 5);

        findByQueryAndVerifyResult(List.of(ClientType.APPLICATION), List.of(ClientCredentialsType.MQTT_BASIC), 4);

        findByQueryAndVerifyResult(List.of(ClientType.APPLICATION), List.of(ClientCredentialsType.SSL), 2);

        findByQueryAndVerifyResult(List.of(ClientType.DEVICE), List.of(ClientCredentialsType.SSL), 3);

        findByQueryAndVerifyResult(List.of(ClientType.DEVICE), List.of(), 8);

        findByQueryAndVerifyResult(null, List.of(ClientCredentialsType.MQTT_BASIC), 9);

        findByQueryAndVerifyResult(null, List.of(ClientCredentialsType.SSL), 5);

        findByQueryAndVerifyResult(List.of(ClientType.APPLICATION), List.of(), 6);
    }

    @Test
    public void givenCachedBasicCredentials_whenSaveOtherCredentials_thenCachedDataPresent() throws JsonProcessingException {
        String cacheKey = RandomStringUtils.randomAlphabetic(10);
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));

        Assert.assertEquals(savedCredentials, basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedBasicCredentials_whenUpdateSslCredentials_thenCachedDataPresent() throws JsonProcessingException {
        String cacheKey = RandomStringUtils.randomAlphabetic(10);
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        sslCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentialsService.saveCredentials(sslCredentials);
        Assert.assertEquals(savedCredentials, basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedBasicCredentials_whenUpdateBasicCredentials_thenCachedDataRemoved() throws JsonProcessingException {
        String cacheKey = RandomStringUtils.randomAlphabetic(10);
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        MqttClientCredentials basicCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        basicCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentialsService.saveCredentials(basicCredentials);

        Assert.assertNull(basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));

        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        savedCredentials.setClientType(ClientType.APPLICATION);
        mqttClientCredentialsService.saveCredentials(savedCredentials);

        Assert.assertNull(basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedBasicCredentials_whenDeleteSslCredentials_thenCachedDataPresent() throws JsonProcessingException {
        String cacheKey = RandomStringUtils.randomAlphabetic(10);
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));

        mqttClientCredentialsService.deleteCredentials(sslCredentials.getId());
        Assert.assertEquals(savedCredentials, basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedBasicCredentials_whenDeleteBasicCredentials_thenCachedDataRemoved() throws JsonProcessingException {
        String cacheKey = RandomStringUtils.randomAlphabetic(10);
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE));
        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        MqttClientCredentials basicCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        mqttClientCredentialsService.deleteCredentials(basicCredentials.getId());

        Assert.assertNull(basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));

        basicCredentialsPasswordCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.deleteCredentials(savedCredentials.getId());

        Assert.assertNull(basicCredentialsPasswordCache.get(cacheKey, MqttClientCredentials.class));
    }

    /**
     * For tests of sslRegexBasedCredentialsCache simply MqttClientCredentials value is used for caching
     */

    @Test
    public void givenCachedSslRegexCredentials_whenSaveBasicCredentials_thenCacheDataPresent() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        Assert.assertEquals(savedCredentials, sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenSaveSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteBasicCredentials_thenCacheDataPresent() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        MqttClientCredentials basicCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        mqttClientCredentialsService.deleteCredentials(basicCredentials.getId());
        Assert.assertEquals(savedCredentials, sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.deleteCredentials(savedCredentials.getId());

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteDifferentSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.deleteCredentials(sslCredentials.getId());

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenUpdateDifferentSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        sslCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentialsService.saveCredentials(sslCredentials);

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenUpdateSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.SSL;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        savedCredentials.setClientType(ClientType.APPLICATION);
        mqttClientCredentialsService.saveCredentials(savedCredentials);

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    private void findByQueryAndVerifyResult(List<ClientType> clientTypes, List<ClientCredentialsType> clientCredentialsTypes, int expected) {
        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), clientTypes, clientCredentialsTypes);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(expected, pageData.getData().size());
    }

    private MqttClientCredentials validMqttClientCredentials(String credentialsName, String clientId, String username, String password) throws JsonProcessingException {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName(credentialsName);
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials basicMqttCredentials = BasicMqttCredentials.newInstance(clientId, username, password, null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));
        return clientCredentials;
    }

    private MqttClientCredentials validMqttBasicClientCredentials(ClientType clientType) throws JsonProcessingException {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName(RandomStringUtils.randomAlphabetic(5));
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        clientCredentials.setClientType(clientType);
        BasicMqttCredentials basicMqttCredentials = BasicMqttCredentials.newInstance(
                RandomStringUtils.randomAlphabetic(5),
                RandomStringUtils.randomAlphabetic(5),
                RandomStringUtils.randomAlphabetic(5),
                null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));
        return clientCredentials;
    }

    private MqttClientCredentials validMqttSslClientCredentials(ClientType clientType) throws JsonProcessingException {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName(RandomStringUtils.randomAlphabetic(5));
        clientCredentials.setCredentialsType(ClientCredentialsType.SSL);
        clientCredentials.setClientType(clientType);
        SslMqttCredentials sslMqttCredentials = SslMqttCredentials.newInstance(
                RandomStringUtils.randomAlphabetic(5),
                RandomStringUtils.randomAlphabetic(5),
                null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(sslMqttCredentials));
        return clientCredentials;
    }

    private MqttClientCredentials validScramMqttClientCredentials() throws Exception {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName("valid scram credentials");
        clientCredentials.setCredentialsType(ClientCredentialsType.SCRAM);
        ScramMqttCredentials scramMqttCredentials = ScramMqttCredentials.newInstance("username", "password", ScramAlgorithm.SHA_512, null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(scramMqttCredentials));
        return clientCredentials;
    }
}
