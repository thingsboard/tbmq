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
package org.thingsboard.mqtt.broker.integration.service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TbmqIeHealthController {

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/health")
    public ResponseEntity<HealthComponent> checkHealth() {
        long startTs = System.nanoTime();
        HealthComponent health = healthEndpoint.health();
        long endTs = System.nanoTime();

        if (health instanceof SystemHealth systemHealth) {
            log.info("[{}] Health check: {} took {} nanos", systemHealth.getStatus(), systemHealth.getComponents(), endTs - startTs);
        } else {
            log.info("[{}] Health check took {} nanos", health.getStatus(), endTs - startTs);
        }

        HttpStatus httpStatus = (Status.UP.equals(health.getStatus())) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(health, httpStatus);
    }
}
