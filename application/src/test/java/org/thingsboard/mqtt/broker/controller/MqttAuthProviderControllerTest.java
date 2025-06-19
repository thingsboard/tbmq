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
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.MqttAuthProviderUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@DaoSqlTest
public class MqttAuthProviderControllerTest extends AbstractControllerTest {

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();
        loginSysAdmin();
    }

    @Test
    public void updateDefaultBasicMqttAuthProviderTest() throws Exception {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC);
        assertThat(mqttAuthProvider.isEnabled()).isFalse();
        assertThat(mqttAuthProvider.getConfiguration()).isInstanceOf(BasicMqttAuthProviderConfiguration.class);

        // enable
        mqttAuthProvider.setEnabled(true);

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", mqttAuthProvider, MqttAuthProvider.class);

        assertThat(savedMqttAuthProvider).isNotNull();
        assertThat(savedMqttAuthProvider.getId()).isEqualTo(mqttAuthProvider.getId());
        assertThat(savedMqttAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(savedMqttAuthProvider.getConfiguration()).isInstanceOf(BasicMqttAuthProviderConfiguration.class);
        assertThat(savedMqttAuthProvider.isEnabled()).isTrue();

        BasicMqttAuthProviderConfiguration savedConfiguration = (BasicMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        assertThat(savedConfiguration).isEqualTo(mqttAuthProvider.getConfiguration());

        MqttAuthProvider foundMqttAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId().toString(), MqttAuthProvider.class);
        assertThat(foundMqttAuthProvider).isNotNull().isEqualTo(savedMqttAuthProvider);
    }

    @Test
    public void updateDefaultJwtMqttAuthProviderTest() throws Exception {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        assertThat(mqttAuthProvider.isEnabled()).isFalse();
        assertThat(mqttAuthProvider.getConfiguration()).isInstanceOf(JwtMqttAuthProviderConfiguration.class);
        JwtMqttAuthProviderConfiguration configuration = (JwtMqttAuthProviderConfiguration) mqttAuthProvider.getConfiguration();
        assertThat(configuration.getDefaultClientType()).isEqualTo(ClientType.DEVICE);

        // enable + change default clientType to APPLICATION.
        configuration.setDefaultClientType(ClientType.APPLICATION);
        mqttAuthProvider.setEnabled(true);
        mqttAuthProvider.setConfiguration(configuration);

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", mqttAuthProvider, MqttAuthProvider.class);

        assertThat(savedMqttAuthProvider).isNotNull();
        assertThat(savedMqttAuthProvider.getId()).isEqualTo(mqttAuthProvider.getId());
        assertThat(savedMqttAuthProvider.getType()).isEqualTo(MqttAuthProviderType.JWT);
        assertThat(savedMqttAuthProvider.getConfiguration()).isInstanceOf(JwtMqttAuthProviderConfiguration.class);
        assertThat(savedMqttAuthProvider.isEnabled()).isTrue();

        JwtMqttAuthProviderConfiguration savedConfiguration = (JwtMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        assertThat(savedConfiguration.getDefaultClientType()).isEqualTo(ClientType.APPLICATION);

        MqttAuthProvider foundMqttAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId().toString(), MqttAuthProvider.class);
        assertThat(foundMqttAuthProvider).isNotNull().isEqualTo(savedMqttAuthProvider);
    }

    @Test
    public void updateDefaultX509MqttAuthProviderTest() throws Exception {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(MqttAuthProviderType.X_509);
        assertThat(mqttAuthProvider.isEnabled()).isFalse();
        assertThat(mqttAuthProvider.getConfiguration()).isInstanceOf(SslMqttAuthProviderConfiguration.class);
        SslMqttAuthProviderConfiguration configuration = (SslMqttAuthProviderConfiguration) mqttAuthProvider.getConfiguration();
        assertThat(configuration.isSkipValidityCheckForClientCert()).isFalse();

        // enable and set skipValidityCheckForClientCert to true
        configuration.setSkipValidityCheckForClientCert(true);
        mqttAuthProvider.setEnabled(true);
        mqttAuthProvider.setConfiguration(configuration);

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", mqttAuthProvider, MqttAuthProvider.class);

        assertThat(savedMqttAuthProvider).isNotNull();
        assertThat(savedMqttAuthProvider.getId()).isEqualTo(mqttAuthProvider.getId());
        assertThat(savedMqttAuthProvider.getType()).isEqualTo(MqttAuthProviderType.X_509);
        assertThat(savedMqttAuthProvider.getConfiguration()).isInstanceOf(SslMqttAuthProviderConfiguration.class);
        assertThat(savedMqttAuthProvider.isEnabled()).isTrue();

        SslMqttAuthProviderConfiguration savedConfiguration = (SslMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        assertThat(savedConfiguration.isSkipValidityCheckForClientCert()).isEqualTo(true);

        MqttAuthProvider foundMqttAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId().toString(), MqttAuthProvider.class);
        assertThat(foundMqttAuthProvider).isNotNull().isEqualTo(savedMqttAuthProvider);
    }

    @Test
    public void updateDefaultScramMqttAuthProviderTest() throws Exception {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(MqttAuthProviderType.SCRAM);
        assertThat(mqttAuthProvider.isEnabled()).isFalse();
        assertThat(mqttAuthProvider.getConfiguration()).isInstanceOf(ScramMqttAuthProviderConfiguration.class);

        // enable
        mqttAuthProvider.setEnabled(true);

        MqttAuthProvider savedMqttAuthProvider = doPost("/api/mqtt/auth/provider", mqttAuthProvider, MqttAuthProvider.class);

        assertThat(savedMqttAuthProvider).isNotNull();
        assertThat(savedMqttAuthProvider.getId()).isEqualTo(mqttAuthProvider.getId());
        assertThat(savedMqttAuthProvider.getType()).isEqualTo(MqttAuthProviderType.SCRAM);
        assertThat(savedMqttAuthProvider.getConfiguration()).isInstanceOf(ScramMqttAuthProviderConfiguration.class);
        assertThat(savedMqttAuthProvider.isEnabled()).isTrue();

        ScramMqttAuthProviderConfiguration savedConfiguration = (ScramMqttAuthProviderConfiguration) savedMqttAuthProvider.getConfiguration();
        assertThat(savedConfiguration).isEqualTo(mqttAuthProvider.getConfiguration());

        MqttAuthProvider foundMqttAuthProvider = doGet("/api/mqtt/auth/provider/" + savedMqttAuthProvider.getId().toString(), MqttAuthProvider.class);
        assertThat(foundMqttAuthProvider).isNotNull().isEqualTo(savedMqttAuthProvider);
    }

    @Test
    public void getMqttAuthProvidersTest() throws Exception {
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

        assertThat(loadedShortMqttAuthProviders.size()).isEqualTo(4);

        assertThat(loadedShortMqttAuthProviders).contains(MqttAuthProviderUtil.toShortMqttAuthProvider(getMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC)));
        assertThat(loadedShortMqttAuthProviders).contains(MqttAuthProviderUtil.toShortMqttAuthProvider(getMqttAuthProvider(MqttAuthProviderType.JWT)));
        assertThat(loadedShortMqttAuthProviders).contains(MqttAuthProviderUtil.toShortMqttAuthProvider(getMqttAuthProvider(MqttAuthProviderType.X_509)));
        assertThat(loadedShortMqttAuthProviders).contains(MqttAuthProviderUtil.toShortMqttAuthProvider(getMqttAuthProvider(MqttAuthProviderType.SCRAM)));
    }

    @Test
    public void saveAuthProviderWithExistingProviderType() throws Exception {
        MqttAuthProvider anotherBasicProvider = getBasicMqttAuthProvider();
        doPost("/api/mqtt/auth/provider", anotherBasicProvider).andExpect(status().isBadRequest());
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
    public void enableDisableMqttAuthProviderTest() throws Exception {
        MqttAuthProvider mqttBasicProvider = getMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC);
        MqttAuthProvider jwtProvider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        MqttAuthProvider x509Provider = getMqttAuthProvider(MqttAuthProviderType.X_509);
        MqttAuthProvider scramProvider = getMqttAuthProvider(MqttAuthProviderType.SCRAM);

        assertThat(mqttBasicProvider.isEnabled()).isFalse();
        assertThat(jwtProvider.isEnabled()).isFalse();
        assertThat(x509Provider.isEnabled()).isFalse();
        assertThat(scramProvider.isEnabled()).isFalse();

        // enable all providers
        doPost("/api/mqtt/auth/provider/" + mqttBasicProvider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + jwtProvider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + x509Provider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + scramProvider.getId() + "/enable").andExpect(status().isOk());

        // verify that all providers are enabled
        MqttAuthProvider fetchedMqttBasicProvider = doGet("/api/mqtt/auth/provider/" + mqttBasicProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedMqttBasicProvider.isEnabled()).isTrue();
        MqttAuthProvider fetchedJwtProvider = doGet("/api/mqtt/auth/provider/" + jwtProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedJwtProvider.isEnabled()).isTrue();
        MqttAuthProvider fetchedX509Provider = doGet("/api/mqtt/auth/provider/" + x509Provider.getId(), MqttAuthProvider.class);
        assertThat(fetchedX509Provider.isEnabled()).isTrue();
        MqttAuthProvider fetchedScramProvider = doGet("/api/mqtt/auth/provider/" + scramProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedScramProvider.isEnabled()).isTrue();

        // enable already enabled providers
        doPost("/api/mqtt/auth/provider/" + mqttBasicProvider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + jwtProvider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + x509Provider.getId() + "/enable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + scramProvider.getId() + "/enable").andExpect(status().isOk());

        // verify that nothing changed
        fetchedMqttBasicProvider = doGet("/api/mqtt/auth/provider/" + mqttBasicProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedMqttBasicProvider.isEnabled()).isTrue();
        fetchedJwtProvider = doGet("/api/mqtt/auth/provider/" + jwtProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedJwtProvider.isEnabled()).isTrue();
        fetchedX509Provider = doGet("/api/mqtt/auth/provider/" + x509Provider.getId(), MqttAuthProvider.class);
        assertThat(fetchedX509Provider.isEnabled()).isTrue();
        fetchedScramProvider = doGet("/api/mqtt/auth/provider/" + scramProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedScramProvider.isEnabled()).isTrue();

        // disable all providers
        doPost("/api/mqtt/auth/provider/" + mqttBasicProvider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + jwtProvider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + x509Provider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + scramProvider.getId() + "/disable").andExpect(status().isOk());

        // verify that all providers are disabled
        fetchedMqttBasicProvider = doGet("/api/mqtt/auth/provider/" + mqttBasicProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedMqttBasicProvider.isEnabled()).isFalse();
        fetchedJwtProvider = doGet("/api/mqtt/auth/provider/" + jwtProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedJwtProvider.isEnabled()).isFalse();
        fetchedX509Provider = doGet("/api/mqtt/auth/provider/" + x509Provider.getId(), MqttAuthProvider.class);
        assertThat(fetchedX509Provider.isEnabled()).isFalse();
        fetchedScramProvider = doGet("/api/mqtt/auth/provider/" + scramProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedScramProvider.isEnabled()).isFalse();

        // disable already disabled providers
        doPost("/api/mqtt/auth/provider/" + mqttBasicProvider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + jwtProvider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + x509Provider.getId() + "/disable").andExpect(status().isOk());
        doPost("/api/mqtt/auth/provider/" + scramProvider.getId() + "/disable").andExpect(status().isOk());

        //  verify that nothing changed
        fetchedMqttBasicProvider = doGet("/api/mqtt/auth/provider/" + mqttBasicProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedMqttBasicProvider.isEnabled()).isFalse();
        fetchedJwtProvider = doGet("/api/mqtt/auth/provider/" + jwtProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedJwtProvider.isEnabled()).isFalse();
        fetchedX509Provider = doGet("/api/mqtt/auth/provider/" + x509Provider.getId(), MqttAuthProvider.class);
        assertThat(fetchedX509Provider.isEnabled()).isFalse();
        fetchedScramProvider = doGet("/api/mqtt/auth/provider/" + scramProvider.getId(), MqttAuthProvider.class);
        assertThat(fetchedScramProvider.isEnabled()).isFalse();
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

    private MqttAuthProvider getBasicMqttAuthProvider() {
        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setEnabled(true);
        provider.setType(MqttAuthProviderType.MQTT_BASIC);
        provider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        return provider;
    }

}
