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
package org.thingsboard.mqtt.broker.controller;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.service.system.ServiceInfoIds;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class AppControllerTest extends AbstractControllerTest {

    private static final String SERVICE_INFO_URL = "/api/app/service/info";
    private static final String SHARED_ID = "app-controller-test-node";

    @Autowired
    private SystemInfoService systemInfoService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CacheProperties cacheProperties;

    private String serviceRegistryKey;

    @Before
    public void beforeTest() throws Exception {
        serviceRegistryKey = cacheProperties.prefixKey(CacheConstants.SERVICE_REGISTRY_KEY);
        register(ServiceType.TBMQ);
        register(ServiceType.TBMQ_INTEGRATION_EXECUTOR);
        loginSysAdmin();
    }

    @After
    public void afterTest() {
        redisTemplate.opsForHash().delete(serviceRegistryKey, SHARED_ID,
                ServiceInfoIds.registryField(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void givenBrokerAndExecutorSharingAnId_whenDeleteExecutor_thenBrokerStaysRegistered() throws Exception {
        doDelete(SERVICE_INFO_URL, "serviceId", SHARED_ID, "serviceType", ServiceType.TBMQ_INTEGRATION_EXECUTOR.name())
                .andExpect(status().isOk());

        assertThat(registryFields()).contains(SHARED_ID)
                .doesNotContain(ServiceInfoIds.registryField(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
        assertThat(systemInfoService.getTbmqServiceIds()).contains(SHARED_ID);
    }

    @Test
    public void givenBrokerAndExecutorSharingAnId_whenDeleteBroker_thenExecutorStaysRegistered() throws Exception {
        doDelete(SERVICE_INFO_URL, "serviceId", SHARED_ID, "serviceType", ServiceType.TBMQ.name())
                .andExpect(status().isOk());

        assertThat(registryFields()).doesNotContain(SHARED_ID)
                .contains(ServiceInfoIds.registryField(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
        assertThat(systemInfoService.getTbmqServiceIds()).doesNotContain(SHARED_ID);
    }

    @Test
    public void givenNoServiceType_whenDelete_thenEveryVariantOfTheIdIsRemoved() throws Exception {
        doDelete(SERVICE_INFO_URL, "serviceId", SHARED_ID).andExpect(status().isOk());

        assertThat(registryFields()).doesNotContain(SHARED_ID,
                ServiceInfoIds.registryField(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void givenUnknownServiceType_whenDelete_thenRejectedAndNothingIsRemoved() throws Exception {
        // A type mismatch falls through BaseController#handleException to GENERAL, hence 500 rather than 400.
        doDelete(SERVICE_INFO_URL, "serviceId", SHARED_ID, "serviceType", "NOT_A_TYPE")
                .andExpect(status().isInternalServerError());

        assertThat(registryFields()).contains(SHARED_ID,
                ServiceInfoIds.registryField(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void givenUnregisteredId_whenDelete_thenNotFound() throws Exception {
        doDelete(SERVICE_INFO_URL, "serviceId", "missing-node", "serviceType", ServiceType.TBMQ.name())
                .andExpect(status().isNotFound());
    }

    private void register(ServiceType serviceType) {
        systemInfoService.processIeServiceInfo(ServiceInfo.newBuilder()
                .setServiceId(SHARED_ID)
                .setServiceType(serviceType.name())
                .build());
    }

    private Set<Object> registryFields() {
        return redisTemplate.opsForHash().keys(serviceRegistryKey);
    }

}
