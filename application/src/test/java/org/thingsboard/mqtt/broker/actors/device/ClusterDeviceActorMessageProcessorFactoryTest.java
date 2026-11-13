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

package org.thingsboard.mqtt.broker.actors.device;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.ClientActorContext;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterDeviceActorMessageProcessorFactoryTest {

    @Test
    void clusterFactoryCreatesTheExistingProcessor() {
        ActorSystemContext systemContext = mock(ActorSystemContext.class);
        ClientActorContext clientActorContext = mock(ClientActorContext.class);
        when(systemContext.getClientActorContext()).thenReturn(clientActorContext);
        when(clientActorContext.getClientLogger()).thenReturn(mock(ClientLogger.class));

        assertThat(new ClusterDeviceActorMessageProcessorFactory().create(systemContext, "c1"))
                .isExactlyInstanceOf(PersistedDeviceActorMessageProcessor.class);
    }

}
