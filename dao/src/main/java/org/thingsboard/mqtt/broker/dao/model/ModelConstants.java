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
    public static final String ENTITY_ID_COLUMN = "entity_id";
    public static final String CLIENT_ID_COLUMN = "client_id";

    /**
     * Timeseries constants.
     */
    public static final String KEY_COLUMN = "key";
    public static final String KEY_ID_COLUMN = "key_id";
    public static final String TS_COLUMN = "ts";
    public static final String LONG_VALUE_COLUMN = "long_v";

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
     * admin_settings constants.
     */
    public static final String ADMIN_SETTINGS_COLUMN_FAMILY_NAME = "admin_settings";
    public static final String ADMIN_SETTINGS_KEY_PROPERTY = "key";
    public static final String ADMIN_SETTINGS_JSON_VALUE_PROPERTY = "json_value";

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
     * mqtt_client_credentials constants.
     */
    public static final String MQTT_CLIENT_CREDENTIALS_COLUMN_FAMILY_NAME = "mqtt_client_credentials";
    public static final String MQTT_CLIENT_CREDENTIALS_TYPE_PROPERTY = "credentials_type";
    public static final String MQTT_CLIENT_CREDENTIALS_ID_PROPERTY = "credentials_id";
    public static final String MQTT_CLIENT_CREDENTIALS_VALUE_PROPERTY = "credentials_value";
    public static final String MQTT_CLIENT_CREDENTIALS_NAME_PROPERTY = "name";
    public static final String MQTT_CLIENT_TYPE_PROPERTY = "client_type";

    /**
     * application_shared_subscription constants.
     */
    public static final String APPLICATION_SHARED_SUBSCRIPTION_COLUMN_FAMILY_NAME = "application_shared_subscription";
    public static final String APPLICATION_SHARED_SUBSCRIPTION_TOPIC_PROPERTY = "topic";
    public static final String APPLICATION_SHARED_SUBSCRIPTION_PARTITIONS_PROPERTY = "partitions";
    public static final String APPLICATION_SHARED_SUBSCRIPTION_NAME_PROPERTY = MQTT_CLIENT_CREDENTIALS_NAME_PROPERTY;

    /**
     * websocket_connection constants.
     */
    public static final String WEBSOCKET_CONNECTION_COLUMN_FAMILY_NAME = "websocket_connection";
    public static final String WEBSOCKET_CONNECTION_CONFIGURATION_PROPERTY = "configuration";
    public static final String WEBSOCKET_CONNECTION_NAME_PROPERTY = MQTT_CLIENT_CREDENTIALS_NAME_PROPERTY;
    public static final String WEBSOCKET_CONNECTION_USER_ID_PROPERTY = USER_CREDENTIALS_USER_ID_PROPERTY;

    /**
     * websocket_subscription constants.
     */
    public static final String WEBSOCKET_SUBSCRIPTION_COLUMN_FAMILY_NAME = "websocket_subscription";
    public static final String WEBSOCKET_SUBSCRIPTION_CONFIGURATION_PROPERTY = WEBSOCKET_CONNECTION_CONFIGURATION_PROPERTY;
    public static final String WEBSOCKET_SUBSCRIPTION_CONNECTION_ID_PROPERTY = "websocket_connection_id";

    /**
     * device_session_ctx constants.
     */
    public static final String DEVICE_SESSION_CTX_COLUMN_FAMILY_NAME = "device_session_ctx";
    public static final String DEVICE_SESSION_CTX_CLIENT_ID_PROPERTY = CLIENT_ID_COLUMN;
    public static final String DEVICE_SESSION_CTX_LAST_UPDATED_PROPERTY = "last_updated_time";
    public static final String DEVICE_SESSION_CTX_LAST_SERIAL_NUMBER_PROPERTY = "last_serial_number";
    public static final String DEVICE_SESSION_CTX_LAST_PACKET_ID_PROPERTY = "last_packet_id";

    /**
     * application_session_ctx constants.
     */
    public static final String APPLICATION_SESSION_CTX_COLUMN_FAMILY_NAME = "application_session_ctx";
    public static final String APPLICATION_SESSION_CTX_CLIENT_ID_PROPERTY = CLIENT_ID_COLUMN;
    public static final String APPLICATION_SESSION_CTX_LAST_UPDATED_PROPERTY = "last_updated_time";
    public static final String APPLICATION_SESSION_CTX_PUBLISH_MSG_INFOS_PROPERTY = "publish_msg_infos";
    public static final String APPLICATION_SESSION_CTX_PUBREL_MSG_INFOS_PROPERTY = "pubrel_msg_infos";

    /**
     * generic_client_session_ctx constants.
     */
    public static final String GENERIC_CLIENT_SESSION_CTX_COLUMN_FAMILY_NAME = "generic_client_session_ctx";
    public static final String GENERIC_CLIENT_SESSION_CTX_CLIENT_ID_PROPERTY = CLIENT_ID_COLUMN;
    public static final String GENERIC_CLIENT_SESSION_CTX_LAST_UPDATED_PROPERTY = "last_updated_time";
    public static final String GENERIC_CLIENT_SESSION_CTX_QOS2_PUBLISH_PACKET_IDS_PROPERTY = "qos2_publish_packet_ids";

    /**
     * unauthorized_client constants.
     */
    public static final String UNAUTHORIZED_CLIENT_COLUMN_FAMILY_NAME = "unauthorized_client";
    public static final String UNAUTHORIZED_CLIENT_CLIENT_ID_PROPERTY = CLIENT_ID_COLUMN;
    public static final String UNAUTHORIZED_CLIENT_IP_ADDRESS_PROPERTY = "ip_address";
    public static final String UNAUTHORIZED_CLIENT_TS_PROPERTY = TS_COLUMN;
    public static final String UNAUTHORIZED_CLIENT_USERNAME_PROPERTY = "username";
    public static final String UNAUTHORIZED_CLIENT_PASSWORD_PROVIDED_PROPERTY = "password_provided";
    public static final String UNAUTHORIZED_CLIENT_TLS_USED_PROPERTY = "tls_used";
    public static final String UNAUTHORIZED_CLIENT_REASON_PROPERTY = "reason";

    /**
     * Integration constants.
     */
    public static final String INTEGRATION_TABLE_NAME = "integration";
    public static final String INTEGRATION_NAME_PROPERTY = "name";
    public static final String INTEGRATION_TYPE_PROPERTY = "type";
    public static final String INTEGRATION_ENABLED_PROPERTY = "enabled";
    public static final String INTEGRATION_CONFIGURATION_PROPERTY = WEBSOCKET_CONNECTION_CONFIGURATION_PROPERTY;
    public static final String INTEGRATION_ADDITIONAL_INFO_PROPERTY = ADDITIONAL_INFO_PROPERTY;
    public static final String INTEGRATION_STATUS_PROPERTY = "status";
    public static final String INTEGRATION_DISCONNECTED_TIME_PROPERTY = "disconnected_time";

    /**
     * Event constants.
     */
    public static final String ERROR_EVENT_TABLE_NAME = "error_event";
    public static final String LC_EVENT_TABLE_NAME = "lc_event";
    public static final String STATS_EVENT_TABLE_NAME = "stats_event";

    public static final String EVENT_SERVICE_ID_PROPERTY = "service_id";
    public static final String EVENT_ENTITY_ID_PROPERTY = ENTITY_ID_COLUMN;

    public static final String EVENT_MESSAGES_PROCESSED_COLUMN_NAME = "e_messages_processed";
    public static final String EVENT_ERRORS_OCCURRED_COLUMN_NAME = "e_errors_occurred";

    public static final String EVENT_METHOD_COLUMN_NAME = "e_method";

    public static final String EVENT_TYPE_COLUMN_NAME = "e_type";
    public static final String EVENT_ERROR_COLUMN_NAME = "e_error";
    public static final String EVENT_SUCCESS_COLUMN_NAME = "e_success";

}
