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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProvider;
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
        MqttClientAuthProvider provider = getBasicMqttClientAuthProvider();

        MqttClientAuthProvider savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttClientAuthProvider.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.BASIC);
        Assert.assertTrue(savedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration() instanceof BasicAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        savedMqttClientAuthProvider.setEnabled(false);

        MqttClientAuthProvider updatedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttClientAuthProvider, MqttClientAuthProvider.class);
        Assert.assertNotNull(updatedMqttClientAuthProvider);
        Assert.assertFalse(updatedMqttClientAuthProvider.isEnabled());
    }

    @Test
    public void saveSslMqttClientAuthProviderTest() throws Exception {
        MqttClientAuthProvider provider = getSslMqttClientAuthProvider();

        MqttClientAuthProvider savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttClientAuthProvider.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.SSL);
        Assert.assertTrue(savedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration() instanceof SslAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        SslAuthProviderConfiguration savedSslConfiguration =
                (SslAuthProviderConfiguration) savedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration();
        Assert.assertTrue(savedSslConfiguration.isSkipValidityCheckForClientCert());


        savedMqttClientAuthProvider.setEnabled(false);
        SslAuthProviderConfiguration savedSslAuthProviderConfiguration
                = (SslAuthProviderConfiguration) savedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration();
        savedSslAuthProviderConfiguration.setSkipValidityCheckForClientCert(false);
        savedMqttClientAuthProvider.setMqttClientAuthProviderConfiguration(savedSslAuthProviderConfiguration);

        MqttClientAuthProvider updatedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttClientAuthProvider, MqttClientAuthProvider.class);
        Assert.assertNotNull(updatedMqttClientAuthProvider);
        Assert.assertFalse(updatedMqttClientAuthProvider.isEnabled());

        SslAuthProviderConfiguration updatedSslConfiguration =
                (SslAuthProviderConfiguration) updatedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration();
        Assert.assertFalse(updatedSslConfiguration.isSkipValidityCheckForClientCert());
    }

    @Test
    public void saveJwtMqttClientAuthProviderTest() throws Exception {
        MqttClientAuthProvider provider = getJwtMqttClientAuthProvider();

        MqttClientAuthProvider savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttClientAuthProvider.class);

        Assert.assertNotNull(savedMqttClientAuthProvider);
        Assert.assertNotNull(savedMqttClientAuthProvider.getId());
        Assert.assertEquals(savedMqttClientAuthProvider.getType(), MqttClientAuthProviderType.JWT);
        Assert.assertTrue(savedMqttClientAuthProvider.getMqttClientAuthProviderConfiguration() instanceof JwtAuthProviderConfiguration);
        Assert.assertTrue(savedMqttClientAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttClientAuthProvider.isEnabled());

        MqttClientAuthProvider foundMqttClientAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId().toString(), MqttClientAuthProvider.class);
        Assert.assertNotNull(foundMqttClientAuthProvider);
        Assert.assertEquals(savedMqttClientAuthProvider, foundMqttClientAuthProvider);
    }

    private MqttClientAuthProvider getBasicMqttClientAuthProvider() {
        MqttClientAuthProvider provider = new MqttClientAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.BASIC);
        provider.setMqttClientAuthProviderConfiguration(new BasicAuthProviderConfiguration());
        return provider;
    }

    private MqttClientAuthProvider getSslMqttClientAuthProvider() {
        SslAuthProviderConfiguration configuration = new SslAuthProviderConfiguration();
        configuration.setSkipValidityCheckForClientCert(true);

        MqttClientAuthProvider provider = new MqttClientAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.SSL);
        provider.setMqttClientAuthProviderConfiguration(configuration);
        return provider;
    }

    private MqttClientAuthProvider getJwtMqttClientAuthProvider() {
        HmacBasedAlgorithmConfiguration algorithmConfiguration = new HmacBasedAlgorithmConfiguration();
        algorithmConfiguration.setSecret(RandomStringUtils.randomAlphanumeric(10));

        AlgorithmBasedVerifierConfiguration verifierConfiguration = new AlgorithmBasedVerifierConfiguration();
        verifierConfiguration.setAlgorithm(JwtSignAlgorithm.HMAC_BASED);
        verifierConfiguration.setJwtSignAlgorithmConfiguration(algorithmConfiguration);

        JwtAuthProviderConfiguration configuration = new JwtAuthProviderConfiguration();
        configuration.setDefaultClientType(ClientType.APPLICATION);
        configuration.setJwtVerifierType(JwtVerifierType.ALGORITHM_BASED);
        configuration.setJwtVerifierConfiguration(verifierConfiguration);

        MqttClientAuthProvider provider = new MqttClientAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttClientAuthProviderType.JWT);
        provider.setMqttClientAuthProviderConfiguration(configuration);
        return provider;
    }

    @Test
    public void getMqttClientAuthProvidersTest() throws Exception {
        MqttClientAuthProvider basicProvider = getBasicMqttClientAuthProvider();
        MqttClientAuthProvider sslProvider = getSslMqttClientAuthProvider();
        MqttClientAuthProvider jwtProvider = getJwtMqttClientAuthProvider();

        MqttClientAuthProvider savedBasicProvider = doPost("/api/mqtt/auth/provider", basicProvider, MqttClientAuthProvider.class);
        MqttClientAuthProvider savedSslProvider = doPost("/api/mqtt/auth/provider", sslProvider, MqttClientAuthProvider.class);
        MqttClientAuthProvider savedJwtProvider = doPost("/api/mqtt/auth/provider", jwtProvider, MqttClientAuthProvider.class);

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
        MqttClientAuthProvider provider = getBasicMqttClientAuthProvider();

        MqttClientAuthProvider savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttClientAuthProvider.class);
        Assert.assertNotNull(savedMqttClientAuthProvider);

        MqttClientAuthProvider anotherBasicProvider = getBasicMqttClientAuthProvider();
        doPost("/api/mqtt/auth/provider", anotherBasicProvider).andExpect(status().isBadRequest());
    }

    @Test
    public void deleteAuthProviderTest() throws Exception {
        MqttClientAuthProvider provider = getBasicMqttClientAuthProvider();

        MqttClientAuthProvider savedMqttClientAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttClientAuthProvider.class);
        Assert.assertNotNull(savedMqttClientAuthProvider);

        doDelete("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId())
                .andExpect(status().isOk());
        doDelete("/api/mqtt/auth/provider/" + savedMqttClientAuthProvider.getId())
                .andExpect(status().isNotFound());
    }

    // data validation

    @Test
    public void saveMqttClientAuthProviderWithNullAuthProviderType() throws Exception {
        MqttClientAuthProvider authProvider = new MqttClientAuthProvider();
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientAuthProviderWithNullAuthProviderConfiguration() throws Exception {
        MqttClientAuthProvider authProvider = new MqttClientAuthProvider();
        authProvider.setType(MqttClientAuthProviderType.JWT);
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

}
