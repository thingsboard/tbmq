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
package org.thingsboard.mqtt.broker.dao.integration;

import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.Dao;

public interface IntegrationDao extends Dao<Integration> {

    /**
     * Find all integrations by page link.
     *
     * @param pageLink the page link
     * @return the list of integration objects
     */
    PageData<Integration> findIntegrations(PageLink pageLink);

    /**
     * Find integration by name.
     * @param name - name of the integration
     * @return - Integration object
     */
    Integration findIntegrationByName(String name);
}
