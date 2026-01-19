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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.util.AuthRulesUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType.JWT;

@Data
@Slf4j
public class JwtClaimsValidator {

    private final JwtMqttAuthProviderConfiguration configuration;
    private final AuthRulePatterns defaultAuthRulePatterns;

    private final boolean hasPubAuthRulesClaim;
    private final boolean hasSubAuthRulesClaim;

    public JwtClaimsValidator(JwtMqttAuthProviderConfiguration configuration, AuthRulePatterns defaultAuthRulePatterns) {
        this.configuration = configuration;
        this.defaultAuthRulePatterns = defaultAuthRulePatterns;
        this.hasPubAuthRulesClaim = StringUtils.isNotBlank(configuration.getPubAuthRuleClaim());
        this.hasSubAuthRulesClaim = StringUtils.isNotBlank(configuration.getSubAuthRuleClaim());
    }

    public AuthResponse validateAll(AuthContext authContext, JWTClaimsSet claims) throws ParseException {
        Date now = new Date();
        Date expirationTime = claims.getExpirationTime();
        Date notBeforeTime = claims.getNotBeforeTime();

        if (expirationTime != null && now.after(expirationTime)) {
            return AuthResponse.skip("JWT token is expired.");
        }
        if (notBeforeTime != null && now.before(notBeforeTime)) {
            return AuthResponse.skip("JWT token not valid yet.");
        }
        if (!validateAuthClaims(authContext, claims)) {
            return AuthResponse.skip("Failed to validate JWT auth claims.");
        }
        ClientType clientType = resolveClientType(claims);
        AuthRulePatterns rulePatterns = resolveAuthRulePatterns(claims);
        return AuthResponse.success(clientType, List.of(rulePatterns), JWT.name());
    }

    private ClientType resolveClientType(JWTClaimsSet claims) throws ParseException {
        var clientTypeClaims = configuration.getClientTypeClaims();
        ClientType defaultClientType = configuration.getDefaultClientType();
        if (clientTypeClaims.isEmpty()) {
            return defaultClientType;
        }
        for (var entry : clientTypeClaims.entrySet()) {
            String actualValue = claims.getClaimAsString(entry.getKey());
            if (!entry.getValue().equals(actualValue)) {
                return defaultClientType;
            }
        }
        return defaultClientType == ClientType.DEVICE ? ClientType.APPLICATION : ClientType.DEVICE;
    }

    private boolean validateAuthClaims(AuthContext authContext, JWTClaimsSet claims) throws ParseException {
        for (var entry : configuration.getAuthClaims().entrySet()) {
            String claimName = entry.getKey();
            String expectedPattern = entry.getValue();

            String expectedValue = switch (expectedPattern) {
                case "${username}" -> authContext.getUsername();
                case "${clientId}" -> authContext.getClientId();
                default -> expectedPattern;
            };

            String claimValue = claims.getClaimAsString(claimName);
            if (!Objects.equals(expectedValue, claimValue)) {
                return false;
            }
        }
        return true;
    }

    private AuthRulePatterns resolveAuthRulePatterns(JWTClaimsSet claims) {
        if (!hasPubAuthRulesClaim && !hasSubAuthRulesClaim) {
            return defaultAuthRulePatterns;
        }
        return AuthRulePatterns.of(
                resolvePatterns(hasPubAuthRulesClaim, claims, configuration.getPubAuthRuleClaim(), defaultAuthRulePatterns.getPubPatterns()),
                resolvePatterns(hasSubAuthRulesClaim, claims, configuration.getSubAuthRuleClaim(), defaultAuthRulePatterns.getSubPatterns())
        );
    }

    private List<Pattern> resolvePatterns(boolean enabled, JWTClaimsSet claims, String claimName, List<Pattern> fallback) {
        if (!enabled) {
            return fallback;
        }
        try {
            List<String> raw = claims.getStringListClaim(claimName);
            return raw == null ? fallback : AuthRulesUtil.fromStringList(raw);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Failed to parse auth rules claim, claims {}", claimName, claims.toString(), e);
            } else {
                log.warn("[{}] Failed to parse auth rules claim", claimName, e);
            }
            return fallback;
        }
    }

}
