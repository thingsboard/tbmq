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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtVerifierType;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.MqttAuthProviderUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@DaoSqlTest
public class MqttAuthProviderControllerTest extends AbstractControllerTest {

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
        PageData<ShortMqttAuthProvider> pageData = doGetTypedWithPageLink("/api/mqtt/auth/providers?",
                new TypeReference<>() {
                }, new PageLink(10));
        List<ShortMqttAuthProvider> shortMqttAuthProviders = new ArrayList<>(pageData.getData());
        for (ShortMqttAuthProvider provider : shortMqttAuthProviders) {
            doDelete("/api/mqtt/auth/provider/" + provider.getId()).andExpect(status().isOk());
        }
    }

    @Test
    public void saveBasicMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);

        Assert.assertNotNull(savedMqttAuthProvider);
        Assert.assertNotNull(savedMqttAuthProvider.getId());
        Assert.assertEquals(MqttAuthProviderType.BASIC, savedMqttAuthProvider.getType());
        Assert.assertTrue(savedMqttAuthProvider.getConfiguration() instanceof BasicMqttAuthProviderConfiguration);
        Assert.assertTrue(savedMqttAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttAuthProvider.isEnabled());

        savedMqttAuthProvider.setEnabled(false);

        MqttAuthProvider updatedMqttAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttAuthProvider, MqttAuthProvider.class);
        Assert.assertNotNull(updatedMqttAuthProvider);
        Assert.assertFalse(updatedMqttAuthProvider.isEnabled());
    }

    @Test
    public void saveSslMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getSslMqttAuthProvider();

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);

        Assert.assertNotNull(savedMqttAuthProvider);
        Assert.assertNotNull(savedMqttAuthProvider.getId());
        Assert.assertEquals(MqttAuthProviderType.X_509, savedMqttAuthProvider.getType());
        Assert.assertTrue(savedMqttAuthProvider.getConfiguration() instanceof SslMqttAuthProviderConfiguration);
        Assert.assertTrue(savedMqttAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttAuthProvider.isEnabled());

        SslMqttAuthProviderConfiguration savedSslConfiguration =
                (SslMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        Assert.assertTrue(savedSslConfiguration.isSkipValidityCheckForClientCert());


        savedMqttAuthProvider.setEnabled(false);
        SslMqttAuthProviderConfiguration savedSslMqttAuthProviderConfiguration
                = (SslMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        savedSslMqttAuthProviderConfiguration.setSkipValidityCheckForClientCert(false);
        savedMqttAuthProvider.setConfiguration(savedSslMqttAuthProviderConfiguration);

        MqttAuthProvider updatedMqttAuthProvider = doPost("/api/mqtt/auth/provider", savedMqttAuthProvider, MqttAuthProvider.class);
        Assert.assertNotNull(updatedMqttAuthProvider);
        Assert.assertFalse(updatedMqttAuthProvider.isEnabled());

        SslMqttAuthProviderConfiguration updatedSslConfiguration =
                (SslMqttAuthProviderConfiguration) updatedMqttAuthProvider.getConfiguration();
        Assert.assertFalse(updatedSslConfiguration.isSkipValidityCheckForClientCert());
    }

    @Test
    public void saveJwtMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getJwtMqttAuthProvider();

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);

        Assert.assertNotNull(savedMqttAuthProvider);
        Assert.assertNotNull(savedMqttAuthProvider.getId());
        Assert.assertEquals(MqttAuthProviderType.JWT, savedMqttAuthProvider.getType());
        Assert.assertTrue(savedMqttAuthProvider.getConfiguration() instanceof JwtMqttAuthProviderConfiguration);
        Assert.assertTrue(savedMqttAuthProvider.getCreatedTime() > 0);
        Assert.assertTrue(savedMqttAuthProvider.isEnabled());

        MqttAuthProvider foundMqttAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId().toString(), MqttAuthProvider.class);
        Assert.assertNotNull(foundMqttAuthProvider);
        Assert.assertEquals(savedMqttAuthProvider, foundMqttAuthProvider);
    }

    private MqttAuthProvider getBasicMqttAuthProvider() {
        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttAuthProviderType.BASIC);
        provider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        return provider;
    }

    private MqttAuthProvider getSslMqttAuthProvider() {
        SslMqttAuthProviderConfiguration configuration = new SslMqttAuthProviderConfiguration();
        configuration.setSkipValidityCheckForClientCert(true);

        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttAuthProviderType.X_509);
        provider.setConfiguration(configuration);
        return provider;
    }

    private MqttAuthProvider getJwtMqttAuthProvider() {
        AlgorithmBasedVerifierConfiguration verifierConfiguration = AlgorithmBasedVerifierConfiguration.defaultConfiguration();

        JwtMqttAuthProviderConfiguration configuration = new JwtMqttAuthProviderConfiguration();
        configuration.setDefaultClientType(ClientType.APPLICATION);
        configuration.setJwtVerifierType(JwtVerifierType.ALGORITHM_BASED);
        configuration.setJwtVerifierConfiguration(verifierConfiguration);
        configuration.setAuthRules(PubSubAuthorizationRules.newInstance(Collections.emptyList()));

        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttAuthProviderType.JWT);
        provider.setConfiguration(configuration);
        return provider;
    }

    @Test
    public void getMqttAuthProvidersTest() throws Exception {
        MqttAuthProvider basicProvider = getBasicMqttAuthProvider();
        MqttAuthProvider sslProvider = getSslMqttAuthProvider();
        MqttAuthProvider jwtProvider = getJwtMqttAuthProvider();

        MqttAuthProvider savedBasicProvider = doPost("/api/mqtt/auth/provider", basicProvider, MqttAuthProvider.class);
        MqttAuthProvider savedSslProvider = doPost("/api/mqtt/auth/provider", sslProvider, MqttAuthProvider.class);
        MqttAuthProvider savedJwtProvider = doPost("/api/mqtt/auth/provider", jwtProvider, MqttAuthProvider.class);

        List<ShortMqttAuthProvider> loadedShortMqttAuthProviders = new ArrayList<>();
        PageLink pageLink = new PageLink(1);
        PageData<ShortMqttAuthProvider> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/mqtt/auth/providers?",
                    new TypeReference<>() {
                    }, pageLink);

            loadedShortMqttAuthProviders.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Assert.assertEquals(3, loadedShortMqttAuthProviders.size());
        Assert.assertTrue(loadedShortMqttAuthProviders
                .contains(MqttAuthProviderUtil.toShortMqttAuthProvider(savedBasicProvider)));
        Assert.assertTrue(loadedShortMqttAuthProviders
                .contains(MqttAuthProviderUtil.toShortMqttAuthProvider(savedSslProvider)));
        Assert.assertTrue(loadedShortMqttAuthProviders
                .contains(MqttAuthProviderUtil.toShortMqttAuthProvider(savedJwtProvider)));
    }

    public void saveAuthProviderWithExistingProviderType() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertNotNull(savedMqttAuthProvider);

        MqttAuthProvider anotherBasicProvider = getBasicMqttAuthProvider();
        doPost("/api/mqtt/auth/provider", anotherBasicProvider).andExpect(status().isBadRequest());
    }

    @Test
    public void deleteAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertNotNull(savedMqttAuthProvider);

        doDelete("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId())
                .andExpect(status().isOk());
        doDelete("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId())
                .andExpect(status().isNotFound());
    }

    // data validation

    @Test
    public void saveMqttAuthProviderWithNullAuthProviderType() throws Exception {
        MqttAuthProvider authProvider = new MqttAuthProvider();
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttAuthProviderWithNullAuthProviderConfiguration() throws Exception {
        MqttAuthProvider authProvider = new MqttAuthProvider();
        authProvider.setType(MqttAuthProviderType.JWT);
        doPost("/api/mqtt/auth/provider", authProvider)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void enableMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();
        provider.setEnabled(false);

        MqttAuthProvider savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertNotNull(savedProvider);
        Assert.assertFalse(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/enable").andExpect(status().isOk());

        MqttAuthProvider fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProvider.class);
        Assert.assertTrue(fetched.isEnabled());
    }

    @Test
    public void disableMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();

        MqttAuthProvider savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertNotNull(savedProvider);
        Assert.assertTrue(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/disable").andExpect(status().isOk());

        MqttAuthProvider fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProvider.class);
        Assert.assertFalse(fetched.isEnabled());
    }

    @Test
    public void enableAlreadyEnabledMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();

        MqttAuthProvider savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertTrue(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/enable").andExpect(status().isOk());

        MqttAuthProvider fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProvider.class);
        Assert.assertTrue(fetched.isEnabled());
    }

    @Test
    public void disableAlreadyDisabledMqttAuthProviderTest() throws Exception {
        MqttAuthProvider provider = getBasicMqttAuthProvider();
        provider.setEnabled(false);

        MqttAuthProvider savedProvider = doPost("/api/mqtt/auth/provider", provider, MqttAuthProvider.class);
        Assert.assertFalse(savedProvider.isEnabled());

        doPost("/api/mqtt/auth/provider/" + savedProvider.getId() + "/disable").andExpect(status().isOk());

        MqttAuthProvider fetched = doGet("/api/mqtt/auth/provider/" + savedProvider.getId(), MqttAuthProvider.class);
        Assert.assertFalse(fetched.isEnabled());
    }

    @Test
    public void enableNonExistingMqttAuthProviderTest() throws Exception {
        doPost("/api/mqtt/auth/provider/ec570977-b00d-48bf-b22e-e42ae4642c1e/enable")
                .andExpect(status().isNotFound());
    }

    @Test
    public void disableNonExistingMqttAuthProviderTest() throws Exception {
        doPost("/api/mqtt/auth/provider/8a5d7cd0-fb82-4763-a602-48bbaa010dd7/disable")
                .andExpect(status().isNotFound());
    }

}
