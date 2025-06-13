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
package org.thingsboard.mqtt.broker.service.auth.providers.ssl;

import lombok.Getter;

@Getter
public enum SslAuthFailure {

    FAILED_TO_GET_CLIENT_CERT_CN("Failed to get client's certificate common name"),
    X_509_AUTH_FAILURE("X509 auth failure: %s"),
    PEER_IDENTITY_NOT_VERIFIED("Peer's identity has not been verified"),
    NO_CERTS_IN_CHAIN("There are no certificates in the chain"),
    FAILED_TO_GET_CERT_CN("Could not get Common Name from certificate"),
    NO_AUTH_RULES_FOR_CN_IN_CREDS("Cannot find authorization rules for common name [%s] from credentials [%s]"),
    SSL_HANDLER_NOT_CONSTRUCTED("Could not authenticate client using X_509_CERTIFICATE_CHAIN credentials since SSL handler is not constructed!"),
    NO_X_509_CREDS_FOUND("Failed to authenticate client using X_509_CERTIFICATE_CHAIN credentials! No X_509_CERTIFICATE_CHAIN matching credentials were found!"),
    CAN_NOT_PARSE_SSL_CREDS("Cannot parse SslMqttCredentials");

    private final String errorMsg;

    SslAuthFailure(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
