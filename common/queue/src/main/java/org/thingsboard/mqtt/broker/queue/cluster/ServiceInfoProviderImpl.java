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
package org.thingsboard.mqtt.broker.queue.cluster;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Setter
public class ServiceInfoProviderImpl implements ServiceInfoProvider {

    private static final String INTEGRATIONS_NONE = "NONE";
    private static final String INTEGRATIONS_ALL = "ALL";

    @Value("${service.id:#{null}}")
    private String serviceId;

    @Value("${service.type:tbmq}")
    private String serviceTypeStr;

    @Value("${service.integrations.supported:ALL}")
    private String supportedIntegrationsStr;

    @Value("${service.integrations.excluded:NONE}")
    private String excludedIntegrationsStr;

    private ServiceType serviceType;
    private ServiceInfo serviceInfo;

    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(serviceId)) {
            serviceId = generateServiceId();
        }
        log.info("Current Service ID: {}", serviceId);
        serviceType = ServiceType.of(serviceTypeStr);
        this.serviceInfo = ServiceInfo.newBuilder()
                .setServiceId(serviceId)
                .build();
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String getServiceType() {
        return serviceTypeStr;
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public List<IntegrationType> getSupportedIntegrationTypes() {
        return serviceType.equals(ServiceType.TBMQ_INTEGRATION_EXECUTOR) ? collectSupportedIntegrationTypes() : Collections.emptyList();
    }

    private List<IntegrationType> collectSupportedIntegrationTypes() {
        List<IntegrationType> excludedIntegrationTypes = getIntegrationTypes(excludedIntegrationsStr, "Failed to parse excluded integration types: {}");
        return getIntegrationTypes(supportedIntegrationsStr, "Failed to parse supplied integration types: {}")
                .stream()
                .filter(it -> !excludedIntegrationTypes.contains(it))
                .collect(Collectors.toList());
    }

    List<IntegrationType> getIntegrationTypes(String integrationsStr, String errorMessage) {
        List<IntegrationType> integrationTypes;
        if (StringUtils.isEmpty(integrationsStr) || integrationsStr.equalsIgnoreCase(INTEGRATIONS_NONE)) {
            integrationTypes = Collections.emptyList();
        } else if (integrationsStr.equalsIgnoreCase(INTEGRATIONS_ALL)) {
            integrationTypes = Arrays.asList(IntegrationType.values());
        } else {
            try {
                integrationTypes = collectToList(integrationsStr);
            } catch (RuntimeException e) {
                log.warn(errorMessage, integrationsStr);
                throw e;
            }
        }
        return integrationTypes;
    }

    private static List<IntegrationType> collectToList(String integrationsStr) {
        return Arrays.stream(integrationsStr.split(","))
                .map(String::trim)
                .map(IntegrationType::valueOf)
                .collect(Collectors.toList());
    }

    String generateServiceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return RandomStringUtils.randomAlphabetic(10);
        }
    }
}
