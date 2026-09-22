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
package org.thingsboard.mqtt.broker.service.drain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeDrainHealthIndicatorTest {

    private final NodeDrainService nodeDrainService = mock(NodeDrainService.class);
    private final NodeDrainHealthIndicator indicator = new NodeDrainHealthIndicator(nodeDrainService);

    @Test
    void givenActiveNode_whenHealthChecked_thenReportsUp() {
        when(nodeDrainService.getStatus()).thenReturn(status(NodeDrainState.ACTIVE, 10));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void givenDrainingNode_whenHealthChecked_thenReportsOutOfServiceWithRemainingSessions() {
        when(nodeDrainService.getStatus()).thenReturn(status(NodeDrainState.DRAINING, 7));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("state", NodeDrainState.DRAINING)
                .containsEntry("remainingSessions", 7);
    }

    private NodeDrainStatus status(NodeDrainState state, int remainingSessions) {
        return new NodeDrainStatus(state, 10, remainingSessions, 3, 100, 0);
    }

}
