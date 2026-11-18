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
package org.thingsboard.mqtt.broker.standalone.store;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StandaloneManagementDataInitializerTest {

    @Test
    void givenTwoSteps_whenInit_thenTheyRunInTheGivenOrder() {
        ManagementDataStep first = mock(ManagementDataStep.class);
        ManagementDataStep second = mock(ManagementDataStep.class);

        new StandaloneManagementDataInitializer(List.of(first, second)).init();

        InOrder order = inOrder(first, second);
        order.verify(first).apply();
        order.verify(second).apply();
    }

    @Test
    void givenNoSteps_whenInit_thenNothingHappens() {
        new StandaloneManagementDataInitializer(List.of()).init();
    }

    @Test
    void givenAFailingStep_whenInit_thenStartupStopsWithTheStepsClassNameAndLaterStepsDoNotRun() {
        ManagementDataStep failing = new FailingStep();
        ManagementDataStep later = mock(ManagementDataStep.class);

        assertThatThrownBy(() -> new StandaloneManagementDataInitializer(List.of(failing, later)).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FailingStep.class.getName())
                .hasMessageContaining("disk is full")
                .hasCauseInstanceOf(UnsupportedOperationException.class);
        verify(later, never()).apply();
    }

    static class FailingStep implements ManagementDataStep {
        @Override
        public void apply() {
            throw new UnsupportedOperationException("disk is full");
        }
    }

}
