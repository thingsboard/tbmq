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
package org.thingsboard.mqtt.broker.service.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntegrationCleanupServiceImplTest {

    static final String INTEGRATION_ID = "0198e1a0-1111-2222-3333-444455556666";

    @Mock
    IntegrationService integrationService;
    @Mock
    IntegrationTopicService integrationTopicService;

    @InjectMocks
    IntegrationCleanupServiceImpl service;

    @Test
    void givenIntegrationId_whenDeleteIntegrationTopic_thenDeletesBothDataAndEventTopics() {
        service.deleteIntegrationTopic(INTEGRATION_ID);

        verify(integrationTopicService).deleteTopic(INTEGRATION_ID, CallbackUtil.EMPTY);
        verify(integrationTopicService).deleteEventTopic(INTEGRATION_ID, CallbackUtil.EMPTY);
    }

}
