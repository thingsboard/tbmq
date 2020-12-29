/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.model;


public class ModelConstants {

    private ModelConstants() {
    }

    /**
     * Generic constants.
     */
    public static final String ID_PROPERTY = "id";
    public static final String CREATED_TIME_PROPERTY = "created_time";
    public static final String ADDITIONAL_INFO_PROPERTY = "additional_info";


    /**
     * broker_user constants.
     */
    public static final String USER_COLUMN_FAMILY_NAME = "broker_user";
    public static final String USER_EMAIL_PROPERTY = "email";
    public static final String USER_AUTHORITY_PROPERTY = "authority";
    public static final String USER_FIRST_NAME_PROPERTY = "first_name";
    public static final String USER_LAST_NAME_PROPERTY = "last_name";
    public static final String USER_ADDITIONAL_INFO_PROPERTY = ADDITIONAL_INFO_PROPERTY;


    /**
     * user_credentials constants.
     */
    public static final String USER_CREDENTIALS_COLUMN_FAMILY_NAME = "user_credentials";
    public static final String USER_CREDENTIALS_USER_ID_PROPERTY = "user_id";
    public static final String USER_CREDENTIALS_ENABLED_PROPERTY = "enabled";
    public static final String USER_CREDENTIALS_PASSWORD_PROPERTY = "password"; //NOSONAR, the constant used to identify password column name (not password value itself)
    public static final String USER_CREDENTIALS_ACTIVATE_TOKEN_PROPERTY = "activate_token";
    public static final String USER_CREDENTIALS_RESET_TOKEN_PROPERTY = "reset_token";


    /**
     * mqtt_client constants.
     */
    public static final String MQTT_CLIENT_COLUMN_FAMILY_NAME = "mqtt_client";
    public static final String MQTT_CLIENT_CLIENT_ID_PROPERTY = "client_id";
    public static final String MQTT_CLIENT_NAME_PROPERTY = "name";
    public static final String MQTT_CLIENT_CREATED_BY_PROPERTY = "created_by";


    /**
     * mqtt_client_credentials constants.
     */
    public static final String MQTT_CLIENT_CREDENTIALS_COLUMN_FAMILY_NAME = "mqtt_client_credentials";
    public static final String MQTT_CLIENT_CREDENTIALS_TYPE_PROPERTY = "credentials_type";
    public static final String MQTT_CLIENT_CREDENTIALS_ID_PROPERTY = "credentials_id";
    public static final String MQTT_CLIENT_CREDENTIALS_VALUE_PROPERTY = "credentials_value";
    public static final String MQTT_CLIENT_CREDENTIALS_CLIENT_ID_PROPERTY = MQTT_CLIENT_CLIENT_ID_PROPERTY;

}
