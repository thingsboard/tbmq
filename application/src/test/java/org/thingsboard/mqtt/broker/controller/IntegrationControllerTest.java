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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class IntegrationControllerTest extends AbstractControllerTest {

    private final AbstractServiceTest.IdComparator<Integration> idComparator = new AbstractServiceTest.IdComparator<>();

    @MockBean
    private IntegrationManagerService integrationManagerService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
        when(integrationManagerService.validateIntegrationConfiguration(any())).thenReturn(Futures.immediateFuture(null));
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        PageData<Integration> pageData = doGetTypedWithPageLink("/api/integrations?",
                new TypeReference<>() {
                }, new PageLink(10_000));
        List<Integration> loadedIntegrations = new ArrayList<>(pageData.getData());
        for (Integration integration : loadedIntegrations) {
            doDelete("/api/integration/" + integration.getId()).andExpect(status().isOk());
        }
    }

    @Test
    public void testSaveIntegration() throws Exception {
        Integration integration = new Integration();
        integration.setName("My integration");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = doPost("/api/integration", integration, Integration.class);

        Assert.assertNotNull(savedIntegration);
        Assert.assertNotNull(savedIntegration.getId());
        Assert.assertTrue(savedIntegration.getCreatedTime() > 0);

        savedIntegration.setName("My new integration");
        doPost("/api/integration", savedIntegration, Integration.class);

        Integration foundIntegration = doGet("/api/integration/" + savedIntegration.getId().toString(), Integration.class);
        Assert.assertEquals(foundIntegration.getName(), savedIntegration.getName());
    }

    @Test
    public void testFindIntegrationById() throws Exception {
        Integration integration = new Integration();
        integration.setName("My integration");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = doPost("/api/integration", integration, Integration.class);
        Integration foundIntegration = doGet("/api/integration/" + savedIntegration.getId().toString(), Integration.class);
        Assert.assertNotNull(foundIntegration);
        Assert.assertEquals(savedIntegration, foundIntegration);
    }

    @Test
    public void testDeleteIntegration() throws Exception {
        Integration integration = new Integration();
        integration.setName("My integration");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = doPost("/api/integration", integration, Integration.class);

        doDelete("/api/integration/" + savedIntegration.getId().toString())
                .andExpect(status().isOk());

        doGet("/api/integration/" + savedIntegration.getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveIntegrationWithEmptyType() throws Exception {
        Integration integration = new Integration();
        integration.setName("My integration");
        doPost("/api/integration", integration)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Integration type should be specified")));
    }

    @Test
    public void testFindIntegrations() throws Exception {
        List<Integration> integrationList = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            Integration integration = new Integration();
            integration.setName("Integration" + i);
            integration.setType(IntegrationType.HTTP);
            integration.setConfiguration(getIntegrationConfiguration());
            integrationList.add(doPost("/api/integration", integration, Integration.class));
        }
        List<Integration> loadedIntegrations = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<Integration> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/integrations?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedIntegrations.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        integrationList.sort(idComparator);
        loadedIntegrations.sort(idComparator);

        Assert.assertEquals(integrationList, loadedIntegrations);
    }

    @Test
    public void testFindIntegrationsBySearchText() throws Exception {
        String title1 = "Integration title 1";
        List<Integration> integrations1 = new ArrayList<>();
        for (int i = 0; i < 143; i++) {
            Integration integration = new Integration();
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            integration.setName(name);
            integration.setType(IntegrationType.HTTP);
            integration.setConfiguration(getIntegrationConfiguration());
            integrations1.add(doPost("/api/integration", integration, Integration.class));
        }
        String title2 = "Integration title 2";
        List<Integration> integrations2 = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            Integration integration = new Integration();
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            integration.setName(name);
            integration.setType(IntegrationType.HTTP);
            integration.setConfiguration(getIntegrationConfiguration());
            integrations2.add(doPost("/api/integration", integration, Integration.class));
        }

        List<Integration> loadedIntegrations1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Integration> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/integrations?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedIntegrations1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        integrations1.sort(idComparator);
        loadedIntegrations1.sort(idComparator);

        Assert.assertEquals(integrations1, loadedIntegrations1);

        List<Integration> loadedIntegrations2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/integrations?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedIntegrations2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        integrations2.sort(idComparator);
        loadedIntegrations2.sort(idComparator);

        Assert.assertEquals(integrations2, loadedIntegrations2);

        for (Integration integration : loadedIntegrations1) {
            doDelete("/api/integration/" + integration.getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/integrations?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Integration integration : loadedIntegrations2) {
            doDelete("/api/integration/" + integration.getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/integrations?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    private JsonNode getIntegrationConfiguration() {
        ObjectNode result = JacksonUtil.newObjectNode();
        result.putObject("metadata").put("key1", "val1");
        result.putArray("topicFilters").add("#");
        return result;
    }
}
