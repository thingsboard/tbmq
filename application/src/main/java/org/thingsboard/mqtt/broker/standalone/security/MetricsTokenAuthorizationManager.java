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

import org.springframework.http.HttpHeaders;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Grants a request only when its {@code Authorization} header is exactly {@code Bearer <token>} for the configured
 * token: the scheme is case-sensitive and the token is compared byte for byte, in constant time. A blank configured
 * token grants nothing. A configured token with a character outside the visible ASCII range (0x21 to 0x7E) is refused
 * at construction: the servlet container reads header values as ISO-8859-1 and trims surrounding whitespace, so no
 * request could carry it. The token is never part of {@link #toString()}, of a log line or of a message.
 */
public class MetricsTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedHeader;

    /**
     * @throws IllegalStateException when a non-blank token holds a character outside the visible ASCII range; the
     *                               message never contains the token
     */
    public MetricsTokenAuthorizationManager(String configuredToken) {
        if (configuredToken != null && !configuredToken.isBlank() && !isVisibleAscii(configuredToken)) {
            throw new IllegalStateException("TBMQ_METRICS_TOKEN must consist of visible ASCII characters only (0x21 to 0x7E), "
                    + "without spaces: an HTTP header cannot carry any other character reliably");
        }
        this.expectedHeader = configuredToken == null || configuredToken.isBlank()
                ? null
                : (BEARER_PREFIX + configuredToken).getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isVisibleAscii(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AuthorizationResult authorize(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return decide(context);
    }

    /**
     * Spring Security 6.5 still declares {@code check} abstract; it gives the same decision as {@link #authorize}.
     */
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return decide(context);
    }

    private AuthorizationDecision decide(RequestAuthorizationContext context) {
        if (expectedHeader == null) {
            return new AuthorizationDecision(false);
        }
        String header = context.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(MessageDigest.isEqual(expectedHeader, header.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return "MetricsTokenAuthorizationManager";
    }

}
