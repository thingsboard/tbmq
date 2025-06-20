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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import com.nimbusds.jose.jwk.source.JWKSetSourceWithHealthStatusReporting;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.health.HealthReport;
import com.nimbusds.jose.util.health.HealthReportListener;
import com.nimbusds.jose.util.health.HealthStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwksHealthReportingListener implements HealthReportListener<JWKSetSourceWithHealthStatusReporting<SecurityContext>, SecurityContext> {

    @Override
    public void notify(HealthReport<JWKSetSourceWithHealthStatusReporting<SecurityContext>, SecurityContext> healthReport) {
        if (HealthStatus.HEALTHY.equals(healthReport.getHealthStatus())) {
            return;
        }
        log.warn("JWKS source {} degraded! Status: {}, Timestamp: {} Exception: ",
                healthReport.getSource(), healthReport.getHealthStatus(), healthReport.getTimestamp(), healthReport.getException());
    }
}
