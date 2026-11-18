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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.install.ManagementDataInitializer;

import java.util.List;

/**
 * Applies the {@link ManagementDataStep}s, in order, when the bean is created. The beans that read management data
 * at startup depend on it.
 */
@Slf4j
@Component("managementDataInitializer")
@StandaloneComponent
public class StandaloneManagementDataInitializer implements ManagementDataInitializer {

    private final List<ManagementDataStep> steps;

    /**
     * @param steps Spring passes them sorted by {@code @Order}
     */
    public StandaloneManagementDataInitializer(List<ManagementDataStep> steps) {
        this.steps = steps;
    }

    @PostConstruct
    public void init() {
        for (ManagementDataStep step : steps) {
            log.debug("Applying the management data step {}", step.getClass().getName());
            try {
                step.apply();
            } catch (RuntimeException e) {
                throw new IllegalStateException("The management data step " + step.getClass().getName() + " failed: "
                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()), e);
            }
        }
    }

}
