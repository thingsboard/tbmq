/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.util.protocol;

public class ProtocolUtil {
    private static final String USERNAME_CREDENTIALS_ID_PREFIX = "username";
    private static final String CLIENT_ID_CREDENTIALS_ID_PREFIX = "client_id";
    private static final String MIXED_CREDENTIALS_ID_PREFIX = "mixed";
    private static final String SSL_CREDENTIALS_ID_PREFIX = "ssl";

    public static String usernameCredentialsId(String username) {
        return USERNAME_CREDENTIALS_ID_PREFIX + username;
    }

    public static String clientIdCredentialsId(String clientId) {
        return CLIENT_ID_CREDENTIALS_ID_PREFIX + clientId;
    }

    public static String mixedCredentialsId(String username, String clientId) {
        return MIXED_CREDENTIALS_ID_PREFIX + username + "|" + clientId;
    }


    public static String sslCredentialsId(String commonName) {
        return SSL_CREDENTIALS_ID_PREFIX + commonName;
    }


}
