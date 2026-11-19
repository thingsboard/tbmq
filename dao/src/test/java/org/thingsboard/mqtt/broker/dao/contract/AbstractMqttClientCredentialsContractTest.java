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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractMqttClientCredentialsContractTest extends AbstractContractTest {

    private final IdComparator<ShortMqttClientCredentials> idComparator = new IdComparator<>();

    @Autowired
    protected MqttClientCredentialsService mqttClientCredentialsService;
    @Autowired
    protected MqttClientCredentialsDao mqttClientCredentialsDao;
    @Autowired
    protected TbCacheOps tbCacheOps;

    Cache mqttClientCredentialsCache;
    Cache basicCredentialsPasswordCache;
    Cache sslRegexBasedCredentialsCache;

    @Before
    public void setUp() {
        mqttClientCredentialsCache = tbCacheOps.cache(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
        basicCredentialsPasswordCache = tbCacheOps.cache(CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE);
        sslRegexBasedCredentialsCache = tbCacheOps.cache(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE);
        mqttClientCredentialsCache.clear();
        basicCredentialsPasswordCache.clear();
        sslRegexBasedCredentialsCache.clear();
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
                validMqttClientCredentials("test1", "client1", "test1", "password1"));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test2", "client2", "test1", null));
        client3Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test3", "client1", "test2", null));

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
                validMqttClientCredentials("test1", null, "user1", null));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test2", null, "user2", null));

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
                validMqttClientCredentials("test1", "client1", null, null));
        client2Credentials = mqttClientCredentialsService.saveCredentials(
                validMqttClientCredentials("test2", "client2", null, null));

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
    public void givenNamesThatDifferInCase_whenSortedByName_thenCaseIsIgnoredFirstAndLowerCaseComesFirst() throws JsonProcessingException {
        // the order of the default collation of the postgres:17 image, en_US.utf8; every backend gives the same
        List<String> names = List.of("Banana fruit", "apple fruit", "banana fruit", "Apple fruit");
        for (int i = 0; i < names.size(); i++) {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentials(names.get(i), "fruitClient" + i, null, null));
        }

        PageData<ShortMqttClientCredentials> ascending = mqttClientCredentialsService.getCredentials(
                new PageLink(10, 0, "fruit", new SortOrder("name", SortOrder.Direction.ASC)));
        PageData<ShortMqttClientCredentials> descending = mqttClientCredentialsService.getCredentials(
                new PageLink(10, 0, "fruit", new SortOrder("name", SortOrder.Direction.DESC)));

        Assert.assertEquals(List.of("apple fruit", "Apple fruit", "banana fruit", "Banana fruit"),
                ascending.getData().stream().map(ShortMqttClientCredentials::getName).toList());
        Assert.assertEquals(List.of("Banana fruit", "banana fruit", "Apple fruit", "apple fruit"),
                descending.getData().stream().map(ShortMqttClientCredentials::getName).toList());
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
        ClientCredentialsQuery query = new ClientCredentialsQuery(pageLink, List.of(), null, null, null, null);

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

        findByQueryAndVerifyResult(List.of(ClientType.APPLICATION), List.of(ClientCredentialsType.X_509), 2);

        findByQueryAndVerifyResult(List.of(ClientType.DEVICE), List.of(ClientCredentialsType.X_509), 3);

        findByQueryAndVerifyResult(List.of(ClientType.DEVICE), List.of(), 8);

        findByQueryAndVerifyResult(null, List.of(ClientCredentialsType.MQTT_BASIC), 9);

        findByQueryAndVerifyResult(null, List.of(ClientCredentialsType.X_509), 5);

        findByQueryAndVerifyResult(List.of(ClientType.APPLICATION), List.of(), 6);
    }

    @Test
    public void testFindMqttClientCredentialsByQueryWithUsername() throws JsonProcessingException {
        String searchString = "zzzz";

        for (int i = 0; i < 5; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentialsWithNulls(null, null, searchString + i, null));
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

        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), null, null, searchString, null, null);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(5, pageData.getData().size());
    }

    @Test
    public void testFindMqttClientCredentialsByQueryWithClientId() throws JsonProcessingException {
        for (int i = 0; i < 5; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentialsWithNulls(null, "zzzzxxxxcccc" + i, null, null));
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

        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), null, null, null, "zxx", null);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(5, pageData.getData().size());
    }

    @Test
    public void testFindMqttClientCredentialsByQueryWithCertificateCn() throws JsonProcessingException {
        String searchString = "zzzz";

        for (int i = 0; i < 5; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentialsWithNulls(null, null, null, null));
        }
        for (int i = 0; i < 4; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        }
        for (int i = 0; i < 3; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(searchString + i));
        }
        for (int i = 0; i < 2; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        }

        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), null, null, null, null, searchString);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(3, pageData.getData().size());
    }

    @Test
    public void testFindMqttClientCredentialsByQueryWithCertificateCnAndUsername() throws JsonProcessingException {
        String searchString = "zzzz";

        for (int i = 0; i < 5; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentialsWithNulls(null, null, searchString + i, null));
        }
        for (int i = 0; i < 4; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        }
        for (int i = 0; i < 3; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(searchString + i));
        }
        for (int i = 0; i < 2; i++) {
            mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        }

        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), null, null, searchString, null, searchString);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void findAllV2FiltersByTypesAndCredentialFields() throws Exception {
        saveBasic("basic one", "c-1", "dev-1");
        saveBasic("basic two", null, "dev-2");
        saveX509("x509 one", "cn=node-7");
        saveScram("scram one", "scram-user");

        assertThat(countV2(null, List.of(ClientCredentialsType.MQTT_BASIC), null, null, null)).isEqualTo(2);
        assertThat(countV2(null, List.of(ClientCredentialsType.SCRAM), null, null, null)).isEqualTo(1);
        assertThat(countV2(List.of(ClientType.DEVICE), null, null, null, null)).isEqualTo(4);
        assertThat(countV2(List.of(ClientType.APPLICATION), null, null, null, null)).isZero();
        assertThat(countV2(List.of(ClientType.DEVICE, ClientType.APPLICATION), List.of(), null, null, null)).isEqualTo(4);
        // none of the filters: no restriction
        assertThat(countV2(null, null, null, null, null)).isEqualTo(4);
        // an empty string counts as an absent filter
        assertThat(countV2(null, null, "", "", "")).isEqualTo(4);

        // username alone: MQTT_BASIC or SCRAM credentials, matched without regard to case
        assertThat(countV2(null, null, "DEV-1", null, null)).isEqualTo(1);
        assertThat(countV2(null, null, "dev", null, null)).isEqualTo(2);
        assertThat(countV2(null, null, "scram", null, null)).isEqualTo(1);
        assertThat(countV2(null, List.of(ClientCredentialsType.X_509), "dev", null, null)).isZero();
        assertThat(countV2(null, List.of(ClientCredentialsType.SCRAM), "dev", null, null)).isZero();
        // client id alone: MQTT_BASIC credentials only
        assertThat(countV2(null, null, null, "C-1", null)).isEqualTo(1);
        assertThat(countV2(null, null, null, "c-2", null)).isZero();
        // certificate CN alone: X_509 credentials only
        assertThat(countV2(null, null, null, null, "NODE")).isEqualTo(1);
        assertThat(countV2(null, null, null, null, "dev")).isZero();
        // username and client id: MQTT_BASIC credentials that match both
        assertThat(countV2(null, null, "dev-1", "c-1", null)).isEqualTo(1);
        assertThat(countV2(null, null, "dev-2", "c-1", null)).isZero();
        assertThat(countV2(null, null, "scram", "c-1", null)).isZero();
        // a certificate CN together with a username or a client id: nothing
        assertThat(countV2(null, null, "dev", null, "node")).isZero();
        assertThat(countV2(null, null, null, "c-1", "node")).isZero();
        assertThat(countV2(null, null, "dev-1", "c-1", "node")).isZero();
    }

    @Test
    public void findAllV2TextSearchesTheName() throws Exception {
        saveBasic("alpha-1", "ca-1", "ua-1");
        saveBasic("Alpha-2", "ca-2", "ua-2");
        saveBasic("beta", "cb-1", "ub-1");

        PageData<ShortMqttClientCredentials> byName = mqttClientCredentialsService.getCredentialsV2(new ClientCredentialsQuery(
                new PageLink(10, 0, "ALPHA"), null, null, null, null, null));
        PageData<ShortMqttClientCredentials> byNameAndUsername = mqttClientCredentialsService.getCredentialsV2(new ClientCredentialsQuery(
                new PageLink(10, 0, "alpha"), null, null, "ua-2", null, null));

        assertThat(byName.getData()).extracting(ShortMqttClientCredentials::getName).containsExactlyInAnyOrder("alpha-1", "Alpha-2");
        assertThat(byName.getTotalElements()).isEqualTo(2);
        assertThat(byNameAndUsername.getData()).extracting(ShortMqttClientCredentials::getName).containsExactly("Alpha-2");
    }

    @Test
    public void anUnknownSortPropertyIsRejectedAsAnInvalidApiUsage() throws Exception {
        saveBasic("sortable", "c-sort", "u-sort");
        PageLink unknownSort = new PageLink(10, 0, null, new SortOrder("noSuchProperty", SortOrder.Direction.ASC));

        assertThatThrownBy(() -> mqttClientCredentialsService.getCredentials(unknownSort))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> mqttClientCredentialsDao.findAll(unknownSort))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> mqttClientCredentialsService.getCredentialsV2(
                new ClientCredentialsQuery(unknownSort, null, null, null, null, null)))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    public void nonAsciiNamesAreFoundByATextSearchOfAnotherCase() throws Exception {
        saveBasic("\u00DCbung Test", "c-uml", "u-uml");
        saveBasic("Stra\u00DFe Test", "c-sz", "u-sz");
        saveBasic("\u0414\u043E\u043C Test", "c-cyr", "u-cyr");
        saveBasic("plain Test", "c-plain", "u-plain");

        assertThat(searchNames("\u00FCbung")).containsExactly("\u00DCbung Test");
        assertThat(searchNames("\u00DCBUNG")).containsExactly("\u00DCbung Test");
        assertThat(searchNames("stra\u00DFe")).containsExactly("Stra\u00DFe Test");
        assertThat(searchNames("\u0434\u043E\u043C")).containsExactly("\u0414\u043E\u043C Test");
        assertThat(searchNames("\u0414\u041E\u041C")).containsExactly("\u0414\u043E\u043C Test");
        assertThat(searchNames("test")).hasSize(4);
    }

    private List<String> searchNames(String search) {
        return mqttClientCredentialsService.getCredentials(new PageLink(10, 0, search)).getData().stream()
                .map(ShortMqttClientCredentials::getName).toList();
    }

    @Test
    public void findAllV2PagesAndSortsTheMatches() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveBasic("page-" + i, "cp-" + i, "up-" + i);
        }
        saveBasic("other", "co-1", "uo-1");

        PageLink first = new PageLink(2, 0, "page-", new SortOrder("name", SortOrder.Direction.DESC));
        PageData<ShortMqttClientCredentials> page = mqttClientCredentialsService.getCredentialsV2(
                new ClientCredentialsQuery(first, null, List.of(ClientCredentialsType.MQTT_BASIC), null, null, null));
        PageData<ShortMqttClientCredentials> last = mqttClientCredentialsService.getCredentialsV2(
                new ClientCredentialsQuery(first.nextPageLink().nextPageLink(), null, null, null, null, null));

        assertThat(page.getData()).extracting(ShortMqttClientCredentials::getName).containsExactly("page-4", "page-3");
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(last.getData()).extracting(ShortMqttClientCredentials::getName).containsExactly("page-0");
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    public void theSystemWebSocketCredentialIsFoundByItsName() {
        assertThat(mqttClientCredentialsDao.findSystemWebSocketCredentials()).isNull();

        MqttClientCredentials saved = mqttClientCredentialsService.saveSystemWebSocketCredentials();

        assertThat(mqttClientCredentialsDao.findSystemWebSocketCredentials()).isEqualTo(saved);
        assertThat(mqttClientCredentialsDao.findCredentialsByName(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME)).isEqualTo(saved);
        assertThat(mqttClientCredentialsService.findSystemWebSocketCredentials()).isEqualTo(saved);
    }

    @Test
    public void findCredentialsByNameMatchesTheWholeNameCaseSensitively() throws Exception {
        MqttClientCredentials saved = saveBasic("Exact Name", "c-name", "u-name");

        assertThat(mqttClientCredentialsDao.findCredentialsByName("Exact Name")).isEqualTo(saved);
        assertThat(mqttClientCredentialsDao.findCredentialsByName("exact name")).isNull();
        assertThat(mqttClientCredentialsDao.findCredentialsByName("Exact")).isNull();
        assertThat(mqttClientCredentialsDao.findCredentialsByName("missing")).isNull();
    }

    @Test
    public void existsAndFindByCredentialsType() throws Exception {
        MqttClientCredentials basic = saveBasic("basic", "c-1", "u-1");
        MqttClientCredentials scram = saveScram("scram", "scram-user");

        assertThat(mqttClientCredentialsDao.existsByCredentialsType(ClientCredentialsType.MQTT_BASIC)).isTrue();
        assertThat(mqttClientCredentialsDao.existsByCredentialsType(ClientCredentialsType.SCRAM)).isTrue();
        assertThat(mqttClientCredentialsDao.existsByCredentialsType(ClientCredentialsType.X_509)).isFalse();
        assertThat(mqttClientCredentialsDao.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).containsExactly(basic);
        assertThat(mqttClientCredentialsDao.findByCredentialsType(ClientCredentialsType.SCRAM)).containsExactly(scram);
        assertThat(mqttClientCredentialsDao.findByCredentialsType(ClientCredentialsType.X_509)).isEmpty();

        mqttClientCredentialsService.deleteCredentials(basic.getId());

        assertThat(mqttClientCredentialsService.existsByCredentialsType(ClientCredentialsType.MQTT_BASIC)).isFalse();
        assertThat(mqttClientCredentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).isEmpty();
    }

    @Test
    public void findByCredentialsIdAndByCredentialsIds() throws Exception {
        MqttClientCredentials first = saveBasic("first", "c-1", null);
        MqttClientCredentials second = saveBasic("second", null, "u-2");

        assertThat(mqttClientCredentialsDao.findByCredentialsId(first.getCredentialsId())).isEqualTo(first);
        assertThat(mqttClientCredentialsDao.findByCredentialsId("missing")).isNull();
        assertThat(mqttClientCredentialsDao.findAllByCredentialsIds(
                List.of(first.getCredentialsId(), "missing", second.getCredentialsId(), first.getCredentialsId())))
                .containsExactlyInAnyOrder(first, second);
        assertThat(mqttClientCredentialsDao.findAllByCredentialsIds(List.of("missing"))).isEmpty();
        assertThat(mqttClientCredentialsDao.findAllByCredentialsIds(List.of())).isEmpty();
    }

    @Test
    public void aCredentialsIdSharedByTwoCredentialsIsRefused() throws Exception {
        MqttClientCredentials first = saveBasic("first", "c-1", "u-1");
        MqttClientCredentials second = validMqttClientCredentials("second", "c-2", "u-2", null);
        second.setCredentialsId(first.getCredentialsId());

        // the service refuses it by its own check; the DAO by the unique key of the credentials id
        assertThatThrownBy(() -> mqttClientCredentialsDao.save(second))
                .isInstanceOf(EntityConstraintViolationException.class)
                .satisfies(e -> assertThat(EntityConstraintViolationException.isViolationOf(e, "mqtt_client_credentials_id_unq_key")).isTrue());
        assertThat(mqttClientCredentialsDao.findAllByCredentialsIds(List.of(first.getCredentialsId()))).containsExactly(first);
    }

    @Test
    public void removeByIdAnswersTrueWhetherOrNotTheCredentialsExisted() throws Exception {
        MqttClientCredentials saved = saveBasic("to remove", "c-1", "u-1");

        assertThat(mqttClientCredentialsDao.removeById(saved.getId())).isTrue();
        assertThat(mqttClientCredentialsDao.findById(saved.getId())).isNull();
        assertThat(mqttClientCredentialsDao.removeById(saved.getId())).isTrue();
        assertThat(mqttClientCredentialsDao.removeById(UUID.randomUUID())).isTrue();
    }

    protected MqttClientCredentials saveBasic(String name, String clientId, String username) throws JsonProcessingException {
        return mqttClientCredentialsService.saveCredentials(validMqttClientCredentials(name, clientId, username, null));
    }

    protected MqttClientCredentials saveX509(String name, String certCnPattern) throws JsonProcessingException {
        MqttClientCredentials credentials = validMqttSslClientCredentials(certCnPattern);
        credentials.setName(name);
        return mqttClientCredentialsService.saveCredentials(credentials);
    }

    protected MqttClientCredentials saveScram(String name, String username) throws Exception {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(name);
        credentials.setCredentialsType(ClientCredentialsType.SCRAM);
        credentials.setCredentialsValue(JacksonUtil.toString(ScramMqttCredentials.newInstance(username, "password", ScramAlgorithm.SHA_512, null)));
        return mqttClientCredentialsService.saveCredentials(credentials);
    }

    private long countV2(List<ClientType> clientTypes, List<ClientCredentialsType> credentialsTypes,
                         String username, String clientId, String certificateCn) {
        return mqttClientCredentialsService.getCredentialsV2(new ClientCredentialsQuery(
                new PageLink(100), clientTypes, credentialsTypes, username, clientId, certificateCn)).getTotalElements();
    }

    // Commented out to allow independent testing without running it in every build
//    @Test
    public void testPerformanceOfGetCredentialsV2() throws JsonProcessingException {
        List<MqttClientCredentials> credentials = saveRandomCredentials();

        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(10), null, null, null, "clientIdPerf", null);
        testPerformanceByFilteringOneField(query, "using clientId", 1);

        query = new ClientCredentialsQuery(new PageLink(10), null, null, "usernamePerf", null, null);
        testPerformanceByFilteringOneField(query, "using username", 1);

        query = new ClientCredentialsQuery(new PageLink(10), null, null, "usernamePerf", null, "abc");
        testPerformanceByFilteringOneField(query, "using username and certificate CN", 0);

        credentials.forEach(creds -> mqttClientCredentialsService.deleteCredentials(creds.getId()));
    }

    private void testPerformanceByFilteringOneField(ClientCredentialsQuery query, String details, int expected) {
        long start = System.nanoTime();
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        long end = System.nanoTime();

        long ms = TimeUnit.NANOSECONDS.toMillis(end - start);
        System.out.println("Took " + ms + " ms to process getCredentialsV2 by query " + details);

        Assert.assertEquals(expected, pageData.getData().size());
    }

    private List<MqttClientCredentials> saveRandomCredentials() throws JsonProcessingException {
        int size = 100_000;
        List<MqttClientCredentials> credentialsList = new ArrayList<>(size + 1);
        for (int i = 0; i < size; i++) {
            credentialsList.add(mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.DEVICE)));
        }
        credentialsList.add(mqttClientCredentialsService.saveCredentials(validMqttClientCredentials(
                "name", "clientIdPerf", "usernamePerf", "password")));
        return credentialsList;
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
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        Assert.assertEquals(savedCredentials, sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenSaveSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteBasicCredentials_thenCacheDataPresent() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        MqttClientCredentials basicCredentials = mqttClientCredentialsService.saveCredentials(validMqttBasicClientCredentials(ClientType.APPLICATION));
        mqttClientCredentialsService.deleteCredentials(basicCredentials.getId());
        Assert.assertEquals(savedCredentials, sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.deleteCredentials(savedCredentials.getId());

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenDeleteDifferentSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        mqttClientCredentialsService.deleteCredentials(sslCredentials.getId());

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenUpdateDifferentSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        MqttClientCredentials sslCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.APPLICATION));
        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        sslCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentialsService.saveCredentials(sslCredentials);

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    @Test
    public void givenCachedSslRegexCredentials_whenUpdateSslCredentials_thenCacheDataRemoved() throws JsonProcessingException {
        ClientCredentialsType cacheKey = ClientCredentialsType.X_509;
        MqttClientCredentials savedCredentials = mqttClientCredentialsService.saveCredentials(validMqttSslClientCredentials(ClientType.DEVICE));

        sslRegexBasedCredentialsCache.put(cacheKey, savedCredentials);

        savedCredentials.setClientType(ClientType.APPLICATION);
        mqttClientCredentialsService.saveCredentials(savedCredentials);

        Assert.assertNull(sslRegexBasedCredentialsCache.get(cacheKey, MqttClientCredentials.class));
    }

    private void findByQueryAndVerifyResult(List<ClientType> clientTypes, List<ClientCredentialsType> clientCredentialsTypes, int expected) {
        ClientCredentialsQuery query = new ClientCredentialsQuery(new PageLink(130), clientTypes, clientCredentialsTypes, null, null, null);
        PageData<ShortMqttClientCredentials> pageData = mqttClientCredentialsService.getCredentialsV2(query);
        Assert.assertEquals(expected, pageData.getData().size());
    }

    private MqttClientCredentials validMqttClientCredentialsWithNulls(String credentialsName, String clientId, String username, String password) throws JsonProcessingException {
        if (credentialsName == null) {
            credentialsName = RandomStringUtils.randomAlphabetic(5);
        }
        if (clientId == null) {
            clientId = RandomStringUtils.randomAlphabetic(5);
        }
        if (username == null) {
            username = RandomStringUtils.randomAlphabetic(5);
        }
        if (password == null) {
            password = RandomStringUtils.randomAlphabetic(5);
        }
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName(credentialsName);
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials basicMqttCredentials = BasicMqttCredentials.newInstance(clientId, username, password, null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));
        return clientCredentials;
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
        clientCredentials.setCredentialsType(ClientCredentialsType.X_509);
        clientCredentials.setClientType(clientType);
        SslMqttCredentials sslMqttCredentials = SslMqttCredentials.newInstance(
                RandomStringUtils.randomAlphabetic(5),
                RandomStringUtils.randomAlphabetic(5),
                null);
        clientCredentials.setCredentialsValue(JacksonUtil.toString(sslMqttCredentials));
        return clientCredentials;
    }

    private MqttClientCredentials validMqttSslClientCredentials(String certificateCn) throws JsonProcessingException {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setName(RandomStringUtils.randomAlphabetic(5));
        clientCredentials.setCredentialsType(ClientCredentialsType.X_509);
        clientCredentials.setClientType(ClientType.DEVICE);
        SslMqttCredentials sslMqttCredentials = SslMqttCredentials.newInstance(
                certificateCn,
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

    //    @Test
    public void perf_negative_cache_benefit_repeated_wrong_3_ids() {
        List<String> wrongIds = List.of(
                ProtocolUtil.clientIdCredentialsId("wrong-1"),
                ProtocolUtil.clientIdCredentialsId("wrong-2"),
                ProtocolUtil.clientIdCredentialsId("wrong-3")
        );

        int warmup = 200;
        int iters = 100_000;

        tbCacheOps.invalidateSafe(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);

        for (int i = 0; i < warmup; i++) {
            mqttClientCredentialsService.findMatchingCredentials(wrongIds);
        }

        tbCacheOps.invalidateSafe(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
        long coldNs = timeNs(() -> mqttClientCredentialsService.findMatchingCredentials(wrongIds));

        tbCacheOps.invalidateSafe(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);

        mqttClientCredentialsService.findMatchingCredentials(wrongIds);

        long steadyNs = timeNs(() -> {
            for (int i = 0; i < iters; i++) {
                mqttClientCredentialsService.findMatchingCredentials(wrongIds);
            }
        });

        System.out.println("=== perf_negative_cache_benefit_repeated_wrong_3_ids ===");
        System.out.println("Cold (1 call, cache empty): " + ms(coldNs) + " ms, " + us(coldNs) + " us");
        System.out.println("Steady (same 3 wrong ids, " + iters + " calls, after prime): total=" +
                ms(steadyNs) + " ms, avg=" + avgUs(steadyNs, iters) + " us/call");

        tbCacheOps.invalidateSafe(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
    }

    private static long timeNs(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return System.nanoTime() - start;
    }

    private static long ms(long ns) {
        return TimeUnit.NANOSECONDS.toMillis(ns);
    }

    private static double us(long ns) {
        return ns / 1_000.0;
    }

    private static double avgUs(long ns, int iters) {
        return (ns / 1_000.0) / iters;
    }

}
