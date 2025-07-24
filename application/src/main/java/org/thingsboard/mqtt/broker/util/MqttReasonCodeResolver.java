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
package org.thingsboard.mqtt.broker.util;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttReasonCodes.Disconnect;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubAck;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubComp;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRec;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRel;
import io.netty.handler.codec.mqtt.MqttReasonCodes.SubAck;
import io.netty.handler.codec.mqtt.MqttReasonCodes.UnsubAck;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_BANNED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_QUOTA_EXCEEDED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE_5;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_TOPIC_NAME_INVALID;

@Slf4j
public final class MqttReasonCodeResolver {

    public static MqttConnectReturnCode connectionRefusedBanned(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_BANNED : CONNECTION_REFUSED_NOT_AUTHORIZED;
    }

    public static MqttConnectReturnCode connectionRefusedNotAuthorized(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_NOT_AUTHORIZED_5 : CONNECTION_REFUSED_NOT_AUTHORIZED;
    }

    public static MqttConnectReturnCode connectionRefusedClientIdNotValid(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID : CONNECTION_REFUSED_IDENTIFIER_REJECTED;
    }

    public static MqttConnectReturnCode connectionRefusedServerUnavailable(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_SERVER_UNAVAILABLE_5 : CONNECTION_REFUSED_SERVER_UNAVAILABLE;
    }

    public static MqttConnectReturnCode connectionRefusedQuotaExceeded(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_QUOTA_EXCEEDED : CONNECTION_REFUSED_SERVER_UNAVAILABLE;
    }

    public static MqttConnectReturnCode connectionRefusedTopicNameInvalid(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? CONNECTION_REFUSED_TOPIC_NAME_INVALID : CONNECTION_REFUSED_SERVER_UNAVAILABLE;
    }

    public static PubComp packetIdNotFound(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? PubComp.PACKET_IDENTIFIER_NOT_FOUND : null;
    }

    public static PubAck pubAckSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? PubAck.SUCCESS : null;
    }

    public static PubRec pubRecSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? PubRec.SUCCESS : null;
    }

    public static PubRel pubRelSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? PubRel.SUCCESS : null;
    }

    public static PubComp pubCompSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? PubComp.SUCCESS : null;
    }

    public static UnsubAck unsubAckSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? UnsubAck.SUCCESS : null;
    }

    public static PubAck pubAckTopicNameInvalid() {
        return PubAck.TOPIC_NAME_INVALID;
    }

    public static PubAck pubAckNotAuthorized() {
        return PubAck.NOT_AUTHORIZED;
    }

    public static PubAck pubAckError() {
        return PubAck.UNSPECIFIED_ERROR;
    }

    public static PubRec pubRecTopicNameInvalid() {
        return PubRec.TOPIC_NAME_INVALID;
    }

    public static PubRec pubRecNotAuthorized() {
        return PubRec.NOT_AUTHORIZED;
    }

    public static PubRec pubRecError() {
        return PubRec.UNSPECIFIED_ERROR;
    }

    public static SubAck notAuthorizedSubscribe(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? SubAck.NOT_AUTHORIZED : failure();
    }

    public static SubAck implementationSpecificError(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? SubAck.IMPLEMENTATION_SPECIFIC_ERROR : failure();
    }

    public static SubAck failure() {
        return SubAck.UNSPECIFIED_ERROR;
    }

    public static Disconnect disconnect(DisconnectReasonType type) {
        return switch (type) {
            case ON_DISCONNECT_MSG -> Disconnect.NORMAL_DISCONNECT;
            case ON_DISCONNECT_AND_WILL_MSG -> Disconnect.DISCONNECT_WITH_WILL_MESSAGE;
            case ON_CONFLICTING_SESSIONS -> Disconnect.SESSION_TAKEN_OVER;
            case ON_CHANNEL_CLOSED -> Disconnect.IMPLEMENTATION_SPECIFIC_ERROR;
            case ON_RATE_LIMITS -> Disconnect.MESSAGE_RATE_TOO_HIGH;
            case ON_KEEP_ALIVE -> Disconnect.KEEP_ALIVE_TIMEOUT;
            case ON_MALFORMED_PACKET -> Disconnect.MALFORMED_PACKET;
            case ON_PROTOCOL_ERROR -> Disconnect.PROTOCOL_ERROR;
            case ON_TOPIC_ALIAS_INVALID -> Disconnect.TOPIC_ALIAS_INVALID;
            case ON_QUOTA_EXCEEDED -> Disconnect.QUOTA_EXCEEDED;
            case ON_SUBSCRIPTION_ID_NOT_SUPPORTED -> Disconnect.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED;
            case ON_PACKET_TOO_LARGE -> Disconnect.PACKET_TOO_LARGE;
            case ON_RECEIVE_MAXIMUM_EXCEEDED -> Disconnect.RECEIVE_MAXIMUM_EXCEEDED;
            case ON_ADMINISTRATIVE_ACTION -> Disconnect.ADMINISTRATIVE_ACTION;
            case ON_NOT_AUTHORIZED -> Disconnect.NOT_AUTHORIZED;
            case ON_SERVER_BUSY -> Disconnect.SERVER_BUSY;
            case ON_SERVER_SHUTTING_DOWN -> Disconnect.SERVER_SHUTTING_DOWN;
            case ON_TOPIC_FILTER_INVALID -> Disconnect.TOPIC_FILTER_INVALID;
            case ON_TOPIC_NAME_INVALID -> Disconnect.TOPIC_NAME_INVALID;
            case ON_PAYLOAD_FORMAT_INVALID -> Disconnect.PAYLOAD_FORMAT_INVALID;
            case ON_RETAIN_NOT_SUPPORTED -> Disconnect.RETAIN_NOT_SUPPORTED;
            case ON_QOS_NOT_SUPPORTED -> Disconnect.QOS_NOT_SUPPORTED;
            case ON_USE_ANOTHER_SERVER -> Disconnect.USE_ANOTHER_SERVER;
            case ON_SERVER_MOVED -> Disconnect.SERVER_MOVED;
            case ON_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED -> Disconnect.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED;
            case ON_CONNECTION_RATE_EXCEEDED -> Disconnect.CONNECTION_RATE_EXCEEDED;
            case ON_MAXIMUM_CONNECT_TIME -> Disconnect.MAXIMUM_CONNECT_TIME;
            case ON_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED -> Disconnect.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED;
            case ON_ERROR -> Disconnect.UNSPECIFIED_ERROR;
        };
    }
}
