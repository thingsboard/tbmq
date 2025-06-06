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
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.nio.charset.StandardCharsets;

@Data
public class HmacBasedAlgorithmConfiguration implements JwtSignAlgorithmConfiguration {

    // TODO: we should save base64 formatted value to DB.
    @NoXss
    private String secret;

    @Override
    public JwtSignAlgorithm getAlgorithm() {
        return JwtSignAlgorithm.HMAC_BASED;
    }

    @Override
    public void validate() {
        if (StringUtils.isBlank(secret)) {
            throw new DataValidationException("Secret should be specified for HMAC based algorithm!");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        int minLength = 32;
        if (secretBytes.length < minLength) {
            throw new DataValidationException("HMAC secret is too short! Must be at least 32 bytes.");
        }
    }

    public static HmacBasedAlgorithmConfiguration defaultConfiguration() {
        var config = new HmacBasedAlgorithmConfiguration();
        config.setSecret("please-change-this-32-char-jwt-secret");
        return config;
    }

}
