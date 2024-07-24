/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.security.model.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.config.JwtSettings;
import org.thingsboard.mqtt.broker.service.security.exception.JwtExpiredTokenException;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;

import javax.crypto.spec.SecretKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenFactory {

    private static final String SCOPES = "scopes";
    private static final String USER_ID = "userId";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String ENABLED = "enabled";

    private final JwtSettings settings;

    @Autowired
    public JwtTokenFactory(JwtSettings settings) {
        this.settings = settings;
    }

    /**
     * Factory method for issuing new JWT Tokens.
     */
    public AccessJwtToken createAccessJwtToken(SecurityUser securityUser) {
        if (securityUser.getAuthority() == null) {
            throw new IllegalArgumentException("User doesn't have any privileges");
        }

        List<String> scopes = securityUser.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
        JwtBuilder jwtBuilder = setUpToken(securityUser, scopes, settings.getTokenExpirationTime());

        jwtBuilder.claim(FIRST_NAME, securityUser.getFirstName())
                .claim(LAST_NAME, securityUser.getLastName())
                .claim(ENABLED, securityUser.isEnabled());
        String token = jwtBuilder.compact();

        return new AccessJwtToken(token);
    }

    public SecurityUser parseAccessJwtToken(RawAccessJwtToken rawAccessToken) {
        Jws<Claims> jwsClaims = parseTokenClaims(rawAccessToken.getToken());
        Claims claims = jwsClaims.getPayload();
        String subject = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> scopes = claims.get(SCOPES, List.class);
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("JWT Token doesn't have any scopes");
        }

        SecurityUser securityUser = new SecurityUser(UUID.fromString(claims.get(USER_ID, String.class)));
        securityUser.setEmail(subject);
        securityUser.setAuthority(Authority.parse(scopes.get(0)));
        securityUser.setFirstName(claims.get(FIRST_NAME, String.class));
        securityUser.setLastName(claims.get(LAST_NAME, String.class));
        securityUser.setEnabled(claims.get(ENABLED, Boolean.class));

        return securityUser;
    }

    public JwtToken createRefreshToken(SecurityUser securityUser) {
        String token = setUpToken(securityUser, Collections.singletonList(Authority.REFRESH_TOKEN.name()), settings.getRefreshTokenExpTime())
                .id(UUID.randomUUID().toString())
                .compact();
        return new AccessJwtToken(token);
    }

    public SecurityUser parseRefreshToken(RawAccessJwtToken rawAccessToken) {
        Jws<Claims> jwsClaims = parseTokenClaims(rawAccessToken.getToken());
        Claims claims = jwsClaims.getPayload();
        @SuppressWarnings("unchecked")
        List<String> scopes = claims.get(SCOPES, List.class);
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("Refresh Token doesn't have any scopes");
        }
        if (!scopes.get(0).equals(Authority.REFRESH_TOKEN.name())) {
            throw new IllegalArgumentException("Invalid Refresh Token scope");
        }
        return new SecurityUser(UUID.fromString(claims.get(USER_ID, String.class)));
    }

    public Jws<Claims> parseTokenClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(settings.getTokenSigningKey()))).build()
                    .parseSignedClaims(token);
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT Token", ex);
            throw new BadCredentialsException("Invalid JWT token: ", ex);
        } catch (SignatureException | ExpiredJwtException expiredEx) {
            log.debug("JWT Token is expired", expiredEx);
            throw new JwtExpiredTokenException(token, "JWT Token expired", expiredEx);
        }
    }

    private JwtBuilder setUpToken(SecurityUser securityUser, List<String> scopes, long expirationTime) {
        if (StringUtils.isBlank(securityUser.getEmail())) {
            throw new IllegalArgumentException("Cannot create JWT Token without username/email");
        }

        ClaimsBuilder claimsBuilder = Jwts.claims()
                .subject(securityUser.getEmail())
                .add(USER_ID, securityUser.getId().toString())
                .add(SCOPES, scopes);

        ZonedDateTime currentTime = ZonedDateTime.now();

        claimsBuilder.expiration(Date.from(currentTime.plusSeconds(expirationTime).toInstant()));

        return Jwts.builder()
                .claims(claimsBuilder.build())
                .issuer(settings.getTokenIssuer())
                .issuedAt(Date.from(currentTime.toInstant()))
                .signWith(new SecretKeySpec(Base64.getDecoder().decode(settings.getTokenSigningKey()), "HmacSHA512"), Jwts.SIG.HS512);
    }
}
