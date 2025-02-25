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
package org.thingsboard.mqtt.broker.dao.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@DaoSqlTest
public class IntegrationServiceImplTest extends AbstractServiceTest {

    @Autowired
    IntegrationService integrationService;

    private final IdComparator<Integration> idComparator = new IdComparator<>();
    private final List<Integration> savedIntegrations = new LinkedList<>();

    @Before
    public void beforeRun() {
    }

    @After
    public void after() {
        clearSavedIntegrations();
    }

    private JsonNode getIntegrationConfiguration() {
        ObjectNode result = JacksonUtil.newObjectNode();
        result.putObject("metadata").put("key1", "val1");
        result.putArray("topicFilters").add("#");
        return result;
    }

    @Test
    public void testSaveIntegration() {
        Integration integration = new Integration();
        integration.setName("My integration save");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = integrationService.saveIntegration(integration);

        Assert.assertNotNull(savedIntegration);
        Assert.assertNotNull(savedIntegration.getId());
        Assert.assertTrue(savedIntegration.getCreatedTime() > 0);

        savedIntegration.setName("My new integration");

        integrationService.saveIntegration(savedIntegration);
        Integration foundIntegration = integrationService.findIntegrationById(savedIntegration.getId());
        Assert.assertEquals(foundIntegration.getName(), savedIntegration.getName());

        integrationService.deleteIntegration(savedIntegration);
    }

    @Test
    public void testSaveIntegrationWithInvalidName() {
        Integration integration = new Integration();
        integration.setName(null);
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Assertions.assertThrows(DataValidationException.class, () -> integrationService.saveIntegration(integration));
    }

    @Test
    public void testSaveIntegrationWithInvalidType() {
        Integration integration = new Integration();
        integration.setName("My integration invalid type");
        integration.setType(null);
        integration.setConfiguration(getIntegrationConfiguration());
        Assertions.assertThrows(DataValidationException.class, () -> integrationService.saveIntegration(integration));
    }

    @Test
    public void testUpdateIntegrationType() {
        Integration integration = new Integration();
        integration.setName("My integration invalid update");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = integrationService.saveIntegration(integration);
        savedIntegration.setType(IntegrationType.MQTT);
        Assertions.assertThrows(DataValidationException.class, () -> integrationService.saveIntegration(savedIntegration));

        integrationService.deleteIntegration(savedIntegration);
    }

    @Test
    public void testFindIntegrationById() {
        Integration integration = new Integration();
        integration.setName("My integration find by id");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = integrationService.saveIntegration(integration);
        Integration foundIntegration = integrationService.findIntegrationById(savedIntegration.getId());
        Assert.assertNotNull(foundIntegration);
        Assert.assertEquals(savedIntegration, foundIntegration);
        integrationService.deleteIntegration(savedIntegration);
    }

    @Test
    public void testDeleteIntegration() {
        Integration integration = new Integration();
        integration.setName("My integration delete");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(getIntegrationConfiguration());
        Integration savedIntegration = integrationService.saveIntegration(integration);
        Integration foundIntegration = integrationService.findIntegrationById(savedIntegration.getId());
        Assert.assertNotNull(foundIntegration);
        integrationService.deleteIntegration(savedIntegration);
        foundIntegration = integrationService.findIntegrationById(savedIntegration.getId());
        Assert.assertNull(foundIntegration);
    }

    @Test
    public void testFindIntegrations() {
        List<Integration> integrations = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            Integration integration = new Integration();
            integration.setName("Integration" + i);
            integration.setType(IntegrationType.HTTP);
            integration.setConfiguration(getIntegrationConfiguration());
            integrations.add(integrationService.saveIntegration(integration));
        }

        List<Integration> loadedIntegrations = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<Integration> pageData = null;
        do {
            pageData = integrationService.findIntegrations(pageLink);
            loadedIntegrations.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        integrations.sort(idComparator);
        loadedIntegrations.sort(idComparator);

        Assert.assertEquals(integrations, loadedIntegrations);

        for (Integration integration : integrations) {
            integrationService.deleteIntegration(integration);
        }

        pageLink = new PageLink(33);
        pageData = integrationService.findIntegrations(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void testFindAllIntegrations() {
        final int numIntegrations = 60;

        for (int i = 0; i < numIntegrations; i++) {
            Integration integration = saveIntegration(
                    "INTEGRATION_" + i
            );
            Assert.assertNotNull("Saved integration is null!", integration);
            savedIntegrations.add(integration);
        }

        List<Integration> integrations = integrationService.findAllIntegrations();
        Assert.assertNotNull("List of found integrations is null!", integrations);
        Assert.assertNotEquals("List with integrations expected, but list is empty!", 0, integrations.size());
        Assert.assertEquals("List of found integrations doesn't correspond the size of previously saved integrations!",
                numIntegrations, integrations.size()
        );

        boolean allMatch = savedIntegrations.stream()
                .allMatch(integration ->
                        integrations.stream()
                                .anyMatch(integrationInfo -> integrationInfo.getId().equals(integration.getId())
                                )
                );
        Assert.assertTrue("Found integrations don't correspond the created integrations!", allMatch);
    }

    private Integration saveIntegration(String name) {
        Integration integration = new Integration();
        integration.setName(name);
        integration.setType(IntegrationType.HTTP);
        integration.setEnabled(false);
        integration.setConfiguration(getIntegrationConfiguration());
        return integrationService.saveIntegration(integration);
    }

    private void clearSavedIntegrations() {
        if (!savedIntegrations.isEmpty()) {
            savedIntegrations.forEach(integration ->
                    integrationService.deleteIntegration(integration)
            );
            savedIntegrations.clear();
        }
    }

}
