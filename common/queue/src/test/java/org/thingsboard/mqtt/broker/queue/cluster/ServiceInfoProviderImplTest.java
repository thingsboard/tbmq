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
package org.thingsboard.mqtt.broker.queue.cluster;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServiceInfoProviderImpl.class)
class ServiceInfoProviderImplTest {

    @SpyBean
    private ServiceInfoProviderImpl serviceInfoProvider;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void givenBrokerServiceTypeStr_whenTransformToObject_thenSuccess() {
        ServiceType serviceType = ServiceType.of("tbmq");
        assertThat(serviceType).isEqualTo(ServiceType.TBMQ);
    }

    @Test
    void givenIeServiceTypeStr_whenTransformToObject_thenSuccess() {
        ServiceType serviceType = ServiceType.of("tbmq-integration-executor");
        assertThat(serviceType).isEqualTo(ServiceType.TBMQ_INTEGRATION_EXECUTOR);
    }

    @Test
    void givenInvalidServiceTypeStr_whenTransformToObject_thenFailure() {
        assertThatThrownBy(() -> ServiceType.of("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void testInit_generatesServiceIdIfNotProvided() {
        serviceInfoProvider.init();
        assertThat(serviceInfoProvider.getServiceId()).isNotBlank();
    }

    @Test
    void testInit_usesProvidedServiceId() {
        serviceInfoProvider.setServiceId("custom-service-id");
        serviceInfoProvider.init();
        assertThat(serviceInfoProvider.getServiceId()).isEqualTo("custom-service-id");
    }

    @Test
    void testGetServiceInfo() {
        serviceInfoProvider.setServiceId("service-info-id");
        serviceInfoProvider.init();
        ServiceInfo serviceInfo = serviceInfoProvider.getServiceInfo();
        assertThat(serviceInfo.getServiceId()).isEqualTo("service-info-id");
    }

    @Test
    void testGetSupportedIntegrationTypes_withTBMQIntegrationExecutor() {
        serviceInfoProvider.setServiceTypeStr("tbmq-integration-executor");
        serviceInfoProvider.setSupportedIntegrationsStr("HTTP,KAFKA");
        serviceInfoProvider.setExcludedIntegrationsStr("KAFKA");
        serviceInfoProvider.init();

        List<IntegrationType> supportedTypes = serviceInfoProvider.getSupportedIntegrationTypes();
        assertThat(supportedTypes).containsExactlyInAnyOrder(IntegrationType.HTTP);
    }

    @Test
    void testGetSupportedIntegrationTypes_withTBMQIntegrationExecutorAndSupportedAllExcludedMqtt() {
        serviceInfoProvider.setServiceTypeStr("tbmq-integration-executor");
        serviceInfoProvider.setSupportedIntegrationsStr("ALL");
        serviceInfoProvider.setExcludedIntegrationsStr("KAFKA");
        serviceInfoProvider.init();

        List<IntegrationType> supportedTypes = serviceInfoProvider.getSupportedIntegrationTypes();
        assertThat(supportedTypes).containsExactlyInAnyOrder(Arrays.stream(IntegrationType.values()).filter(type -> !type.equals(IntegrationType.KAFKA)).toArray(IntegrationType[]::new));
    }

    @Test
    void testGetSupportedIntegrationTypes_withNoIntegrationExecutor() {
        serviceInfoProvider.setServiceTypeStr("tbmq");
        serviceInfoProvider.init();

        List<IntegrationType> supportedTypes = serviceInfoProvider.getSupportedIntegrationTypes();
        assertThat(supportedTypes).isEmpty();
    }

    @Test
    void testGenerateServiceId_withHostname() throws UnknownHostException {
        String hostname = InetAddress.getLocalHost().getHostName();
        String serviceId = serviceInfoProvider.generateServiceId();
        assertThat(serviceId).isEqualTo(hostname);
    }

    @Test
    void testGetIntegrationTypes_withAll() {
        List<IntegrationType> types = serviceInfoProvider.getIntegrationTypes("ALL", "Error");
        assertThat(types).containsExactlyInAnyOrder(IntegrationType.values());
    }

    @Test
    void testGetIntegrationTypes_withNone() {
        List<IntegrationType> types = serviceInfoProvider.getIntegrationTypes("NONE", "Error");
        assertThat(types).isEmpty();
    }

    @Test
    void testGetIntegrationTypes_withEmptyString() {
        List<IntegrationType> types = serviceInfoProvider.getIntegrationTypes("", "Error");
        assertThat(types).isEmpty();
    }

    @Test
    void testGetIntegrationTypes_withValidTypes() {
        List<IntegrationType> types = serviceInfoProvider.getIntegrationTypes("HTTP,KAFKA", "Error");
        assertThat(types).containsExactlyInAnyOrder(IntegrationType.HTTP, IntegrationType.KAFKA);
    }

    @Test
    void testGetIntegrationTypes_withInvalidType() {
        assertThatThrownBy(() -> serviceInfoProvider.getIntegrationTypes("INVALID", "Error"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
