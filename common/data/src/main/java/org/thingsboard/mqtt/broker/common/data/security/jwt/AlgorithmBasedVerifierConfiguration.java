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
package org.thingsboard.mqtt.broker.common.data.security.jwt;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

@Data
public class AlgorithmBasedVerifierConfiguration implements JwtVerifierConfiguration {

    @NoXss
    private JwtSignAlgorithm algorithm;
    private JwtSignAlgorithmConfiguration jwtSignAlgorithmConfiguration;

    @Override
    public JwtVerifierType getJwtVerifierType() {
        return JwtVerifierType.ALGORITHM_BASED;
    }

    @Override
    public void validate() {
        if (algorithm == null) {
            throw new DataValidationException("JWT signing algorithm should be specified!");
        }
        if (jwtSignAlgorithmConfiguration == null) {
            throw new DataValidationException("JWT signing algorithm configuration should be specified!");
        }
        jwtSignAlgorithmConfiguration.validate();
    }

    public static AlgorithmBasedVerifierConfiguration defaultConfiguration() {
        var config = new AlgorithmBasedVerifierConfiguration();
        config.setAlgorithm(JwtSignAlgorithm.HMAC_BASED);
        config.setJwtSignAlgorithmConfiguration(HmacBasedAlgorithmConfiguration.defaultConfiguration());
        return config;
    }

}
