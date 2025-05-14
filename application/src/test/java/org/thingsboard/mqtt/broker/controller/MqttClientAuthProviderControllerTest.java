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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderDto;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtSignAlgorithm;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtVerifierType;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.MqttClientAuthProviderUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@DaoSqlTest
public class MqttClientAuthProviderControllerTest extends AbstractControllerTest {

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        PageData<ShortMqttClientAuthProvider> pageData = doGetTypedWithPageLink("/api/mqtt/auth/providers?",
                new TypeReference<>() {
                }, new PageLink(10));
        List<ShortMqttClientAuthProvider> shortMqttClientAuthProviders = new ArrayList<>(pageData.getData());
        for (ShortMqttClientAuthProvider provider : shortMqttClientAuthProviders) {
            doDelete("/api/mqtt/auth/provider/" + provider.getId()).andExpect(status().isOk());
        }
    }

    @Test
    public void saveBasicMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();

        MqttAuthProviderDto savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.BASIC);
        Assert.assertTrue(savedMqttClientAuthProvider.getConfiguration() instanceof BasicAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        savedMqttClientAuthProvider.setEnabled(false);

        MqttAuthProviderDto updatedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttClientAuthProvider, MqttAuthProviderDto.class);
        Assert.assertNotNull(updatedMqttClientAuthProvider);
        Assert.assertFalse(updatedMqttClientAuthProvider.isEnabled());
    }

    @Test
    public void saveSslMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getSslMqttClientAuthProvider();

        MqttAuthProviderDto savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.SSL);
        Assert.assertTrue(savedMqttClientAuthProvider.getConfiguration() instanceof SslAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        SslAuthProviderConfiguration savedSslConfiguration =
                (SslAuthProviderConfiguration) savedMqttClientAuthProvider.getConfiguration();
        Assert.assertTrue(savedSslConfiguration.isSkipValidityCheckForClientCert());


        savedMqttClientAuthProvider.setEnabled(false);
        SslAuthProviderConfiguration savedSslAuthProviderConfiguration
                = (SslAuthProviderConfiguration) savedMqttClientAuthProvider.getConfiguration();
        savedSslAuthProviderConfiguration.setSkipValidityCheckForClientCert(false);
        savedMqttClientAuthProvider.setConfiguration(savedSslAuthProviderConfiguration);

        MqttAuthProviderDto updatedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttClientAuthProvider, MqttAuthProviderDto.class);
        Assert.assertNotNull(updatedMqttClientAuthProvider);
        Assert.assertFalse(updatedMqttClientAuthProvider.isEnabled());

        SslAuthProviderConfiguration updatedSslConfiguration =
                (SslAuthProviderConfiguration) updatedMqttClientAuthProvider.getConfiguration();
        Assert.assertFalse(updatedSslConfiguration.isSkipValidityCheckForClientCert());
    }

    @Test
    public void saveJwtMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getJwtMqttClientAuthProvider();

        MqttAuthProviderDto savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.JWT);
        Assert.assertTrue(savedMqttClientAuthProvider.getConfiguration() instanceof JwtAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        MqttAuthProviderDto foundMqttClientAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId().toString(), MqttAuthProviderDto.class);
        Assert.assertNotNull(foundMqttClientAuthProvider);
        Assert.assertEquals(savedMqttClientAuthProvider, foundMqttClientAuthProvider);
    }

    private MqttAuthProviderDto getBasicMqttClientAuthProvider() {
        MqttAuthProviderDto provider = new MqttAuthProviderDto();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.BASIC);
        provider.setConfiguration(new BasicAuthProviderConfiguration());
        return provider;
    }

    private MqttAuthProviderDto getSslMqttClientAuthProvider() {
        SslAuthProviderConfiguration configuration = new SslAuthProviderConfiguration();
        configuration.setSkipValidityCheckForClientCert(true);

        MqttAuthProviderDto provider = new MqttAuthProviderDto();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.SSL);
        provider.setConfiguration(configuration);
        return provider;
    }

    private MqttAuthProviderDto getJwtMqttClientAuthProvider() {
        HmacBasedAlgorithmConfiguration algorithmConfiguration = new HmacBasedAlgorithmConfiguration();
        algorithmConfiguration.setSecret(RandomStringUtils.randomAlphanumeric(10));

        AlgorithmBasedVerifierConfiguration verifierConfiguration = new AlgorithmBasedVerifierConfiguration();
        verifierConfiguration.setAlgorithm(JwtSignAlgorithm.HMAC_BASED);
        verifierConfiguration.setJwtSignAlgorithmConfiguration(algorithmConfiguration);

        JwtAuthProviderConfiguration configuration = new JwtAuthProviderConfiguration();
        configuration.setDefaultClientType(ClientType.APPLICATION);
        configuration.setJwtVerifierType(JwtVerifierType.ALGORITHM_BASED);
        configuration.setJwtVerifierConfiguration(verifierConfiguration);

        MqttAuthProviderDto provider = new MqttAuthProviderDto();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.JWT);
        provider.setConfiguration(configuration);
        return provider;
    }

    @Test
    public void getMqttClientAuthProvidersTest() throws Exception {
        MqttAuthProviderDto basicProvider = getBasicMqttClientAuthProvider();
        MqttAuthProviderDto sslProvider = getSslMqttClientAuthProvider();
        MqttAuthProviderDto jwtProvider = getJwtMqttClientAuthProvider();

        MqttAuthProviderDto savedBasicProvider = doPost("/api/mqtt/auth/provider", basicProvider, MqttAuthProviderDto.class);
        MqttAuthProviderDto savedSslProvider = doPost("/api/mqtt/auth/provider", sslProvider, MqttAuthProviderDto.class);
        MqttAuthProviderDto savedJwtProvider = doPost("/api/mqtt/auth/provider", jwtProvider, MqttAuthProviderDto.class);

        List<ShortMqttClientAuthProvider> loadedShortMqttClientAuthProviders = new ArrayList<>();
        PageLink pageLink = new PageLink(1);
        PageData<ShortMqttClientAuthProvider> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/mqtt/auth/providers?",
                    new TypeReference<>() {
                    }, pageLink);

            loadedShortMqttClientAuthProviders.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Assert.assertEquals(3, loadedShortMqttClientAuthProviders.size());
        Assert.assertTrue(loadedShortMqttClientAuthProviders
                .contains(MqttClientAuthProviderUtil.toShortMqttClientAuthProvider(savedBasicProvider)));
        Assert.assertTrue(loadedShortMqttClientAuthProviders
                .contains(MqttClientAuthProviderUtil.toShortMqttClientAuthProvider(savedSslProvider)));
        Assert.assertTrue(loadedShortMqttClientAuthProviders
                .contains(MqttClientAuthProviderUtil.toShortMqttClientAuthProvider(savedJwtProvider)));
    }

    public void saveAuthProviderWithExistingProviderType() throws  Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();

        MqttAuthProviderDto savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertNotNull(savedMqttClientAuthProvider);

        MqttAuthProviderDto anotherBasicProvider = getBasicMqttClientAuthProvider();
        doPost("/api/mqtt/auth/provider", anotherBasicProvider).andExpect(status().isBadRequest());
    }

    @Test
    public void deleteAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();

        MqttAuthProviderDto savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertNotNull(savedMqttClientAuthProvider);

        doDelete("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId())
                .andExpect(status().isOk());
        doDelete("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId())
                .andExpect(status().isNotFound());
    }

    // data validation

    @Test
    public void saveMqttClientAuthProviderWithNullAuthProviderType() throws Exception {
        MqttAuthProviderDto authProvider = new MqttAuthProviderDto();
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientAuthProviderWithNullAuthProviderConfiguration() throws Exception {
        MqttAuthProviderDto authProvider = new MqttAuthProviderDto();
        authProvider.setType(MqttClientAuthProviderType.JWT);
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void enableMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();
        provider.setEnabled(false);

        MqttAuthProviderDto savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertNotNull(savedProvider);
        Assert.assertFalse(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/enable").andExpect(status().isOk());

        MqttAuthProviderDto fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProviderDto.class);
        Assert.assertTrue(fetched.isEnabled());
    }

    @Test
    public void disableMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();

        MqttAuthProviderDto savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertNotNull(savedProvider);
        Assert.assertTrue(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/disable").andExpect(status().isOk());

        MqttAuthProviderDto fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProviderDto.class);
        Assert.assertFalse(fetched.isEnabled());
    }

    @Test
    public void enableAlreadyEnabledMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();

        MqttAuthProviderDto savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertTrue(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/enable").andExpect(status().isOk());

        MqttAuthProviderDto fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProviderDto.class);
        Assert.assertTrue(fetched.isEnabled());
    }

    @Test
    public void disableAlreadyDisabledMqttClientAuthProviderTest() throws Exception {
        MqttAuthProviderDto provider = getBasicMqttClientAuthProvider();
        provider.setEnabled(false);

        MqttAuthProviderDto savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProviderDto.class);
        Assert.assertFalse(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/disable").andExpect(status().isOk());

        MqttAuthProviderDto fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProviderDto.class);
        Assert.assertFalse(fetched.isEnabled());
    }

    @Test
    public void enableNonExistingMqttClientAuthProviderTest() throws Exception {
        doPost("/api/mqtt/auth/provider/ec570977-b00d-48bf-b22e-e42ae4642c1e/enable")
                .andExpect(status().isNotFound());
    }

    @Test
    public void disableNonExistingMqttClientAuthProviderTest() throws Exception {
        doPost("/api/mqtt/auth/provider/8a5d7cd0-fb82-4763-a602-48bbaa010dd7/disable")
                .andExpect(status().isNotFound());
    }

}
