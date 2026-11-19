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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.util.List;
import java.util.UUID;

/**
 * The descriptor of every type the entity store keeps. Index values mirror the SQL columns of the same constraint: an
 * enum is indexed by its name, a UUID by its canonical text. A child type is declared before the parents that cascade
 * to it, because a cascade holds the child's descriptor.
 * <p>
 * A type that a DAO pages has a projection. Its sort properties are the scalar attributes of the type's JPA entity under
 * their attribute names ({@code id} and {@code createdTime} included where the entity has them); JSON columns are not
 * sortable. Its text search is the field of the SQL query's {@code LOWER(..) LIKE}, and its filter fields are those the
 * DAO's queries filter on.
 */
public final class EntityDescriptors {

    public static final EntityDescriptor<AdminSettings, UUID> ADMIN_SETTINGS = baseData(StoredEntityType.ADMIN_SETTINGS, AdminSettings.class)
            .index(EntityIndex.ADMIN_SETTINGS_KEY, AdminSettings::getKey)
            .build();

    public static final EntityDescriptor<MqttAuthProvider, UUID> MQTT_AUTH_PROVIDER = baseData(StoredEntityType.MQTT_AUTH_PROVIDER, MqttAuthProvider.class)
            .index(EntityIndex.AUTH_PROVIDER_TYPE, provider -> text(provider.getType()))
            .projection(EntityDescriptors.<MqttAuthProvider>baseDataProjection()
                    .sortAndFilter("enabled", MqttAuthProvider::isEnabled)
                    .sort("type", MqttAuthProvider::getType)
                    .textSearch(provider -> text(provider.getType()))
                    .build())
            .build();

    public static final EntityDescriptor<MqttClientCredentials, UUID> MQTT_CLIENT_CREDENTIALS = baseData(StoredEntityType.MQTT_CLIENT_CREDENTIALS, MqttClientCredentials.class)
            .index(EntityIndex.CREDENTIALS_ID, MqttClientCredentials::getCredentialsId)
            .index(EntityIndex.CREDENTIALS_NAME, MqttClientCredentials::getName)
            .index(EntityIndex.CREDENTIALS_TYPE, credentials -> text(credentials.getCredentialsType()))
            // credentialsValue holds JSON with the credential secrets: not sortable, and only three of its fields are kept
            .projection(EntityDescriptors.<MqttClientCredentials>baseDataProjection()
                    .sort("credentialsId", MqttClientCredentials::getCredentialsId)
                    .sort("name", MqttClientCredentials::getName)
                    .sortAndFilter("clientType", MqttClientCredentials::getClientType)
                    .sortAndFilter("credentialsType", MqttClientCredentials::getCredentialsType)
                    .filters(List.of("userName", "clientId", "certCnPattern"), EntityDescriptors::credentialsValueFields)
                    .textSearch(MqttClientCredentials::getName)
                    .build())
            .build();

    public static final EntityDescriptor<UserCredentials, UUID> USER_CREDENTIALS = baseData(StoredEntityType.USER_CREDENTIALS, UserCredentials.class)
            .index(EntityIndex.USER_CREDENTIALS_USER_ID, credentials -> text(credentials.getUserId()))
            .index(EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, UserCredentials::getActivateToken)
            .index(EntityIndex.USER_CREDENTIALS_RESET_TOKEN, UserCredentials::getResetToken)
            .build();

    public static final EntityDescriptor<WebSocketSubscription, UUID> WEBSOCKET_SUBSCRIPTION = baseData(StoredEntityType.WEBSOCKET_SUBSCRIPTION, WebSocketSubscription.class)
            .index(EntityIndex.WS_SUBSCRIPTION_CONNECTION, subscription -> text(subscription.getWebSocketConnectionId()))
            // the SQL query takes a text search and ignores it
            .projection(EntityDescriptors.<WebSocketSubscription>baseDataProjection()
                    .sortAndFilter("webSocketConnectionId", WebSocketSubscription::getWebSocketConnectionId)
                    .build())
            .build();

    public static final EntityDescriptor<WebSocketConnection, UUID> WEBSOCKET_CONNECTION = baseData(StoredEntityType.WEBSOCKET_CONNECTION, WebSocketConnection.class)
            .index(EntityIndex.WS_CONNECTION_USER_NAME, EntityDescriptors::userAndName)
            .index(EntityIndex.WS_CONNECTION_USER, connection -> text(connection.getUserId()))
            .cascade(EntityIndex.WS_SUBSCRIPTION_CONNECTION, WEBSOCKET_SUBSCRIPTION, "fk_websocket_connection_id")
            .projection(EntityDescriptors.<WebSocketConnection>baseDataProjection()
                    .sort("name", WebSocketConnection::getName)
                    .sortAndFilter("userId", WebSocketConnection::getUserId)
                    .textSearch(WebSocketConnection::getName)
                    .build())
            .build();

    public static final EntityDescriptor<User, UUID> USER = baseData(StoredEntityType.USER, User.class)
            .index(EntityIndex.USER_EMAIL, User::getEmail)
            .cascade(EntityIndex.WS_CONNECTION_USER, WEBSOCKET_CONNECTION, "fk_user_id")
            .projection(EntityDescriptors.<User>baseDataProjection()
                    .sort("authority", User::getAuthority)
                    .sort("email", User::getEmail)
                    .sort("firstName", User::getFirstName)
                    .sort("lastName", User::getLastName)
                    .textSearch(User::getEmail)
                    .build())
            .build();

    public static final EntityDescriptor<UnauthorizedClient, String> UNAUTHORIZED_CLIENT =
            EntityDescriptor.builder(StoredEntityType.UNAUTHORIZED_CLIENT, UnauthorizedClient.class, UnauthorizedClient::getClientId)
                    // the SQL entity has neither an id nor a created time attribute
                    .projection(Projection.<UnauthorizedClient>builder()
                            .sortAndFilter("clientId", UnauthorizedClient::getClientId)
                            .sortAndFilter("ipAddress", UnauthorizedClient::getIpAddress)
                            .sortAndFilter("ts", UnauthorizedClient::getTs)
                            .sortAndFilter("username", UnauthorizedClient::getUsername)
                            .sortAndFilter("passwordProvided", UnauthorizedClient::isPasswordProvided)
                            .sortAndFilter("tlsUsed", UnauthorizedClient::isTlsUsed)
                            .sortAndFilter("reason", UnauthorizedClient::getReason)
                            .textSearch(UnauthorizedClient::getClientId)
                            .build())
                    .build();

    /**
     * Every descriptor, in {@link StoredEntityType} order.
     */
    public static final List<EntityDescriptor<?, ?>> ALL = List.of(ADMIN_SETTINGS, MQTT_AUTH_PROVIDER, MQTT_CLIENT_CREDENTIALS,
            USER, USER_CREDENTIALS, WEBSOCKET_CONNECTION, WEBSOCKET_SUBSCRIPTION, UNAUTHORIZED_CLIENT);

    private EntityDescriptors() {
    }

    private static <T extends BaseData> Projection.Builder<T> baseDataProjection() {
        return Projection.<T>builder()
                .sort("id", BaseData::getId)
                .sort("createdTime", BaseData::getCreatedTime);
    }

    /**
     * The {@code userName}, {@code clientId} and {@code certCnPattern} text fields at the top of the credentials value;
     * {@code null} for each one that is missing or not text, and for all three when the value is not a JSON object.
     */
    private static Object[] credentialsValueFields(MqttClientCredentials credentials) {
        Object[] fields = new Object[3];
        if (credentials.getCredentialsValue() == null) {
            return fields;
        }
        JsonNode value;
        try {
            value = JacksonUtil.OBJECT_MAPPER.readTree(credentials.getCredentialsValue());
        } catch (JsonProcessingException e) {
            return fields;
        }
        if (value != null && value.isObject()) {
            fields[0] = textField(value, "userName");
            fields[1] = textField(value, "clientId");
            fields[2] = textField(value, "certCnPattern");
        }
        return fields;
    }

    private static String textField(JsonNode value, String name) {
        JsonNode field = value.get(name);
        return field != null && field.isTextual() ? field.asText() : null;
    }

    private static <T extends BaseData> EntityDescriptor.Builder<T, UUID> baseData(StoredEntityType type, Class<T> domainClass) {
        return EntityDescriptor.<T, UUID>builder(type, domainClass, BaseData::getId)
                .assignedIds(BaseData::setId, BaseData::setCreatedTime);
    }

    // the SQL unique key is (name, user_id)
    private static String userAndName(WebSocketConnection connection) {
        return connection.getUserId() == null || connection.getName() == null ? null : connection.getUserId() + "\0" + connection.getName();
    }

    private static String text(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String text(UUID value) {
        return value == null ? null : value.toString();
    }

}
