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
package org.thingsboard.mqtt.broker.standalone.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.config.JwtSettings;
import org.thingsboard.mqtt.broker.dao.store.StoreSecrets;

import java.util.Base64;

/**
 * Makes the per-install key of the store the REST JWT signing key, unless the operator set one of their own. The key
 * of {@link JwtSettings} is read on every sign and verify, so replacing it before the web server starts serving is
 * enough. Neither key is ever logged.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneJwtSigningKey {

    /**
     * The default of {@code security.jwt.tokenSigningKey} in {@code thingsboard-mqtt-broker.yml}: a value equal to it
     * means the operator set none.
     */
    static final String YML_DEFAULT = "Qk1xUnloZ0VQTlF1VlNJQXZ4cWhiNWt1cVd1ZzQ5cWpENUhMSHlaYmZIM0JrZ2pPTVlhQ3N1Z0ZMUnd0SDBieg==";

    private final JwtSettings jwtSettings;
    private final StoreSecrets storeSecrets;

    public StandaloneJwtSigningKey(JwtSettings jwtSettings, StoreSecrets storeSecrets) {
        this.jwtSettings = jwtSettings;
        this.storeSecrets = storeSecrets;
    }

    @PostConstruct
    void init() {
        String configured = jwtSettings.getTokenSigningKey();
        if (configured == null || configured.isBlank() || YML_DEFAULT.equals(configured)) {
            jwtSettings.setTokenSigningKey(Base64.getEncoder().encodeToString(storeSecrets.jwtSigningKey()));
            log.info("Signing the REST JWT tokens with the key of this installation, kept in the store");
        } else {
            log.info("Signing the REST JWT tokens with the key set in security.jwt.tokenSigningKey (JWT_TOKEN_SIGNING_KEY)");
        }
    }

}
