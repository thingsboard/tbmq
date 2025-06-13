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
import org.thingsboard.mqtt.broker.common.data.util.SslUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;

@Data
public class PemKeyAlgorithmConfiguration implements JwtSignAlgorithmConfiguration {

    @NoXss
    private String publicPemKey;

    @Override
    public JwtSignAlgorithm getAlgorithm() {
        return JwtSignAlgorithm.PEM_KEY;
    }

    @Override
    public void validate() {
        if (StringUtils.isBlank(publicPemKey)) {
            throw new DataValidationException("Public PEM key should be specified for PEM based algorithm!");
        }
        try {
            PublicKey pk = SslUtil.readPublicKey(publicPemKey);
            if (pk instanceof RSAPublicKey || pk instanceof ECPublicKey) {
                return;
            }
            if (pk instanceof EdECPublicKey edECPublicKey &&
                "Ed25519".equals(edECPublicKey.getParams().getName())) {
                    return;
            }
            throw new DataValidationException("Only RSA, EC, or Ed25519 public keys are supported!");
        } catch (Exception e) {
            throw new DataValidationException("Failed to parse public PEM key: " + e.getMessage(), e);
        }
    }

}
