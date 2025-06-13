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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwksVerifierConfiguration;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

import javax.net.ssl.SSLContext;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Data
public class JwksVerificationStrategy implements JwtVerificationStrategy {

    private final static long JWK_SET_CACHE_REFRESH_TIMEOUT = TimeUnit.MINUTES.toMillis(1); // How long a verification thread is willing to block while fetching / refreshing
    private static final int MIN_REFRESH_AHEAD_CACHE_TIME = 30000; // 10 % from JWK_SET_CACHE_REFRESH_TIMEOUT in millis. Time before TTL cleanup within which Nimbus will fetch a fresh JWKS in the background.
    private final static long OUTAGE_TTL = TimeUnit.HOURS.toMillis(24); // Enables outage tolerance by serving a non-expiring cached JWK set in case of outage.

    private final JWKSource<SecurityContext> jwkSource;
    private final JwtClaimsValidator claimsValidator;
    private final DefaultJWSVerifierFactory defaultJWSVerifierFactory;
    private final ConcurrentMap<JWSAlgorithm, JWSKeySelector<SecurityContext>> selectorCache;

    public JwksVerificationStrategy(JwksVerifierConfiguration configuration, JwtClaimsValidator jwtClaimsValidator) {
        this.jwkSource = initializeJWKSource(configuration);
        this.claimsValidator = jwtClaimsValidator;
        this.defaultJWSVerifierFactory = new DefaultJWSVerifierFactory();
        this.selectorCache = new ConcurrentHashMap<>();
    }

    @Override
    public AuthResponse authenticateJwt(AuthContext authContext, String jwt) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        JWSHeader header = signedJWT.getHeader();
        JWSAlgorithm alg = header.getAlgorithm();

        JWSKeySelector<SecurityContext> selector = selectorCache
                .computeIfAbsent(alg, a -> new JWSVerificationKeySelector<>(alg, jwkSource));
        List<? extends Key> keys = selector.selectJWSKeys(header, null);

        if (keys.isEmpty()) {
            return AuthResponse.failure("No matching key found in JWKS for JWT verification.");
        }

        JWSVerifier verifier = defaultJWSVerifierFactory.createJWSVerifier(header, keys.get(0));
        if (!signedJWT.verify(verifier)) {
            return AuthResponse.failure("JWT signature validation failed.");
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        return claimsValidator.validateAll(authContext, claims);
    }

    // DOCS: https://connect2id.com/products/nimbus-jose-jwt/examples/enhanced-jwk-retrieval
    private JWKSource<SecurityContext> initializeJWKSource(JwksVerifierConfiguration configuration) {
        try {
            URL jwksURL = new URL(configuration.getEndpoint());
            long ttlMillis = TimeUnit.SECONDS.toMillis(configuration.getRefreshInterval());
            return JWKSourceBuilder.create(jwksURL, createResourceRetriever(configuration))
                    .cache(ttlMillis, JWK_SET_CACHE_REFRESH_TIMEOUT)
                    .refreshAheadCache(Math.max(MIN_REFRESH_AHEAD_CACHE_TIME, ttlMillis / 10), true)
                    .retrying(true)
                    .outageTolerant(OUTAGE_TTL)
                    .healthReporting(new JwksHealthReportingListener())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DefaultResourceRetriever createResourceRetriever(JwksVerifierConfiguration configuration) throws NoSuchAlgorithmException {
        ClientCredentials clientCredentials = configuration.getCredentials();
        SSLContext sslContext = clientCredentials.initJSSESslContext();
        Map<String, List<String>> convertedHeaders = prepareResourceRetrieverHeaders(configuration, clientCredentials);
        var resourceRetriever = new DefaultResourceRetriever(0, 0, 0, true, sslContext.getSocketFactory());
        resourceRetriever.setHeaders(convertedHeaders);
        return resourceRetriever;
    }

    private Map<String, List<String>> prepareResourceRetrieverHeaders(JwksVerifierConfiguration configuration, ClientCredentials clientCredentials) {
        Map<String, String> headers = configuration.getHeaders();
        if (clientCredentials instanceof BasicCredentials basicCredentials) {
            String authString = basicCredentials.getUsername() + ":" + basicCredentials.getPassword();
            String encodedAuthString = new String(Base64.getEncoder().encode(authString.getBytes(StandardCharsets.UTF_8)));
            headers.put("Authorization", "Basic " + encodedAuthString);
        }
        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue())
                ));
    }

}
