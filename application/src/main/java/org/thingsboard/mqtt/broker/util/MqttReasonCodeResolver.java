/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_QUOTA_EXCEEDED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE_5;

public final class MqttReasonCodeResolver {

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

    public static MqttReasonCodes.PubComp packetIdNotFound(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.PubComp.PACKET_IDENTIFIER_NOT_FOUND : null;
    }

    public static MqttReasonCodes.PubAck pubAckSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.PubAck.SUCCESS : null;
    }

    public static MqttReasonCodes.PubRec pubRecSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.PubRec.SUCCESS : null;
    }

    public static MqttReasonCodes.PubRel pubRelSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.PubRel.SUCCESS : null;
    }

    public static MqttReasonCodes.PubComp pubCompSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.PubComp.SUCCESS : null;
    }

    public static MqttReasonCodes.UnsubAck unsubAckSuccess(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.UnsubAck.SUCCESS : null;
    }

    public static MqttReasonCodes.PubAck pubAckTopicNameInvalid() {
        return MqttReasonCodes.PubAck.TOPIC_NAME_INVALID;
    }

    public static MqttReasonCodes.PubAck pubAckNotAuthorized() {
        return MqttReasonCodes.PubAck.NOT_AUTHORIZED;
    }

    public static MqttReasonCodes.PubRec pubRecTopicNameInvalid() {
        return MqttReasonCodes.PubRec.TOPIC_NAME_INVALID;
    }

    public static MqttReasonCodes.PubRec pubRecNotAuthorized() {
        return MqttReasonCodes.PubRec.NOT_AUTHORIZED;
    }

    public static MqttReasonCodes.SubAck notAuthorizedSubscribe(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.SubAck.NOT_AUTHORIZED : failure();
    }

    public static MqttReasonCodes.SubAck implementationSpecificError(ClientSessionCtx ctx) {
        return ctx.getMqttVersion() == MqttVersion.MQTT_5 ? MqttReasonCodes.SubAck.IMPLEMENTATION_SPECIFIC_ERROR : failure();
    }

    public static MqttReasonCodes.SubAck failure() {
        return MqttReasonCodes.SubAck.UNSPECIFIED_ERROR;
    }

    public static MqttReasonCodes.Disconnect disconnect(DisconnectReasonType type) {
        return switch (type) {
            case ON_DISCONNECT_MSG -> MqttReasonCodes.Disconnect.NORMAL_DISCONNECT;
            case ON_DISCONNECT_AND_WILL_MSG -> MqttReasonCodes.Disconnect.DISCONNECT_WITH_WILL_MESSAGE;
            case ON_CONFLICTING_SESSIONS -> MqttReasonCodes.Disconnect.SESSION_TAKEN_OVER;
            case ON_CHANNEL_CLOSED -> MqttReasonCodes.Disconnect.ADMINISTRATIVE_ACTION;
            case ON_RATE_LIMITS -> MqttReasonCodes.Disconnect.MESSAGE_RATE_TOO_HIGH;
            case ON_KEEP_ALIVE -> MqttReasonCodes.Disconnect.KEEP_ALIVE_TIMEOUT;
            case ON_MALFORMED_PACKET -> MqttReasonCodes.Disconnect.MALFORMED_PACKET;
            case ON_PROTOCOL_ERROR -> MqttReasonCodes.Disconnect.PROTOCOL_ERROR;
            case ON_TOPIC_ALIAS_INVALID -> MqttReasonCodes.Disconnect.TOPIC_ALIAS_INVALID;
            case ON_QUOTA_EXCEEDED -> MqttReasonCodes.Disconnect.QUOTA_EXCEEDED;
            case ON_SUBSCRIPTION_ID_NOT_SUPPORTED -> MqttReasonCodes.Disconnect.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED;
            case ON_PACKET_TOO_LARGE -> MqttReasonCodes.Disconnect.PACKET_TOO_LARGE;
            case ON_RECEIVE_MAXIMUM_EXCEEDED -> MqttReasonCodes.Disconnect.RECEIVE_MAXIMUM_EXCEEDED;
            default -> MqttReasonCodes.Disconnect.UNSPECIFIED_ERROR;
        };
    }
}
