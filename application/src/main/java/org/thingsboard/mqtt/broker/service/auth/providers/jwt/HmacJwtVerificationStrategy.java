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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Data;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

@Data
public class HmacJwtVerificationStrategy implements JwtVerificationStrategy {

    private final MACVerifier verifier;
    private final JwtClaimsValidator claimsValidator;

    public HmacJwtVerificationStrategy(String rawSecret, JwtClaimsValidator claimsValidator) throws JOSEException {
        this.verifier = new MACVerifier(rawSecret);
        this.claimsValidator = claimsValidator;
    }

    @Override
    public AuthResponse authenticateJwt(AuthContext authContext, String jwt) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        if (!signedJWT.verify(verifier)) {
            return AuthResponse.failure("JWT signature validation failed.");
        }
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        return claimsValidator.validateAll(authContext, claims);
    }
}
