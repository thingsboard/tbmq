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
package org.thingsboard.mqtt.broker.cache;

public class CacheConstants {

    public static final String PACKET_ID_AND_SERIAL_NUMBER_CACHE = "packetIdAndSerialNumber";
    public static final String MQTT_CLIENT_CREDENTIALS_CACHE = "mqttClientCredentials";
    public static final String BASIC_CREDENTIALS_PASSWORD_CACHE = "basicCredentialsPassword";
    public static final String SSL_REGEX_BASED_CREDENTIALS_CACHE = "sslRegexBasedCredentials";
    public static final String CLIENT_SESSION_CREDENTIALS_CACHE = "clientSessionCredentials";
    public static final String CLIENT_SESSIONS_LIMIT_CACHE = "clientSessionsLimit";
    public static final String APP_CLIENTS_LIMIT_CACHE = "appClientsLimit";

    public static final String BUCKET_SUFFIX = "::bucket";
    public static final String DEVICE_PERSISTED_MSGS_LIMIT_CACHE = "devicePersistedMsgsLimit" + BUCKET_SUFFIX;
    public static final String TOTAL_MSGS_LIMIT_CACHE = "totalMsgsLimit" + BUCKET_SUFFIX;

    public static final String COUNT_SUFFIX = "::count";
    public static final String CLIENT_SESSIONS_LIMIT_CACHE_KEY = CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE + COUNT_SUFFIX;
    public static final String APP_CLIENTS_LIMIT_CACHE_KEY = CacheConstants.APP_CLIENTS_LIMIT_CACHE + COUNT_SUFFIX;

    public static final String COMMA = ",";
    public static final String COLON = ":";
}
