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

import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;

import java.util.UUID;

/**
 * The descriptor of every type the entity store keeps. Index values mirror the SQL columns of the same constraint: an
 * enum is indexed by its name, a UUID by its canonical text. A child type is declared before the parents that cascade
 * to it, because a cascade holds the child's descriptor.
 */
public final class EntityDescriptors {

    public static final EntityDescriptor<AdminSettings, UUID> ADMIN_SETTINGS = baseData(StoredEntityType.ADMIN_SETTINGS, AdminSettings.class)
            .index(EntityIndex.ADMIN_SETTINGS_KEY, AdminSettings::getKey)
            .build();

    public static final EntityDescriptor<MqttAuthProvider, UUID> MQTT_AUTH_PROVIDER = baseData(StoredEntityType.MQTT_AUTH_PROVIDER, MqttAuthProvider.class)
            .index(EntityIndex.AUTH_PROVIDER_TYPE, provider -> text(provider.getType()))
            .build();

    public static final EntityDescriptor<MqttClientCredentials, UUID> MQTT_CLIENT_CREDENTIALS = baseData(StoredEntityType.MQTT_CLIENT_CREDENTIALS, MqttClientCredentials.class)
            .index(EntityIndex.CREDENTIALS_ID, MqttClientCredentials::getCredentialsId)
            .index(EntityIndex.CREDENTIALS_NAME, MqttClientCredentials::getName)
            .index(EntityIndex.CREDENTIALS_TYPE, credentials -> text(credentials.getCredentialsType()))
            .build();

    public static final EntityDescriptor<UserCredentials, UUID> USER_CREDENTIALS = baseData(StoredEntityType.USER_CREDENTIALS, UserCredentials.class)
            .index(EntityIndex.USER_CREDENTIALS_USER_ID, credentials -> text(credentials.getUserId()))
            .index(EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, UserCredentials::getActivateToken)
            .index(EntityIndex.USER_CREDENTIALS_RESET_TOKEN, UserCredentials::getResetToken)
            .build();

    public static final EntityDescriptor<WebSocketSubscription, UUID> WEBSOCKET_SUBSCRIPTION = baseData(StoredEntityType.WEBSOCKET_SUBSCRIPTION, WebSocketSubscription.class)
            .index(EntityIndex.WS_SUBSCRIPTION_CONNECTION, subscription -> text(subscription.getWebSocketConnectionId()))
            .build();

    public static final EntityDescriptor<WebSocketConnection, UUID> WEBSOCKET_CONNECTION = baseData(StoredEntityType.WEBSOCKET_CONNECTION, WebSocketConnection.class)
            .index(EntityIndex.WS_CONNECTION_USER_NAME, EntityDescriptors::userAndName)
            .index(EntityIndex.WS_CONNECTION_USER, connection -> text(connection.getUserId()))
            .cascade(EntityIndex.WS_SUBSCRIPTION_CONNECTION, WEBSOCKET_SUBSCRIPTION)
            .build();

    public static final EntityDescriptor<User, UUID> USER = baseData(StoredEntityType.USER, User.class)
            .index(EntityIndex.USER_EMAIL, User::getEmail)
            .cascade(EntityIndex.WS_CONNECTION_USER, WEBSOCKET_CONNECTION)
            .build();

    public static final EntityDescriptor<UnauthorizedClient, String> UNAUTHORIZED_CLIENT =
            EntityDescriptor.builder(StoredEntityType.UNAUTHORIZED_CLIENT, UnauthorizedClient.class, UnauthorizedClient::getClientId)
                    .build();

    private EntityDescriptors() {
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
