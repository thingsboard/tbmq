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
package org.thingsboard.mqtt.broker.common.data.credentials;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.security.NoSuchAlgorithmException;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AnonymousCredentials.class, name = "anonymous"),
        @JsonSubTypes.Type(value = BasicCredentials.class, name = "basic"),
        @JsonSubTypes.Type(value = CertPemCredentials.class, name = "cert.PEM")})
public interface ClientCredentials {

    @JsonIgnore
    CredentialsType getType();

    @JsonIgnore
    default SslContext initSslContext() throws SSLException {
        return SslContextBuilder.forClient().build();
    }

    /**
     * Initializes a Javax (JSSE) SSLContext for use in standard HTTPS connections.
     * Used by non-Netty components like JWKS HTTP retrieval.
     */
    @JsonIgnore
    default javax.net.ssl.SSLContext initJSSESslContext() throws NoSuchAlgorithmException {
        return SSLContext.getDefault();
    }

}
