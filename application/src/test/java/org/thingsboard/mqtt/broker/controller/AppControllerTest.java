/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.controller;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.data.audit.ActionType;
import org.thingsboard.mqtt.broker.common.data.audit.EntityType;
import org.thingsboard.mqtt.broker.common.data.id.HasId;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.audit.AuditLogService;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.service.system.ServiceInfoIds;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class AppControllerTest extends AbstractControllerTest {

    private static final String SERVICE_INFO_URL = "/api/app/service/info";
    private static final String SHARED_ID = "app-controller-test-node";

    @MockitoBean
    private AuditLogService auditLogService;
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

        ArgumentCaptor<HasId> entity = ArgumentCaptor.forClass(HasId.class);
        verify(auditLogService).logEntityAction(any(), eq(EntityType.SERVICE_INFO), entity.capture(), eq(SHARED_ID),
                eq(ActionType.DELETED), isNull());
        assertThat(entity.getValue().getId())
                .isEqualTo(ServiceInfoIds.auditEntityId(SHARED_ID, ServiceType.TBMQ_INTEGRATION_EXECUTOR.name()));
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
