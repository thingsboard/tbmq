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
package org.thingsboard.mqtt.broker.server.traffic;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.INCOMING_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.OUTGOING_MSGS;
import static org.thingsboard.mqtt.broker.server.MqttSessionHandler.CLIENT_ID_ATTR;

@Slf4j
@RequiredArgsConstructor
public class DuplexTrafficHandler extends ChannelDuplexHandler {

    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MqttPublishMessage publishMsg) {
            // Netty generates MqttPublishMessage only upon successful decoding
            handleValidPublish(ctx, publishMsg);
        } else if (msg instanceof MqttMessage mqttMessage) {
            // If decoding fails, the result is a generic MqttMessage with a failure result
            handleFailedPublish(mqttMessage);
        }

        ctx.fireChannelRead(msg);
    }

    private void handleValidPublish(ChannelHandlerContext ctx, MqttPublishMessage publishMsg) {
        String clientId = getClientId(ctx);
        if (clientId != null) {
            tbMessageStatsReportClient.reportClientSendStats(clientId, NettyMqttConverter.extractPublishQos(publishMsg));
        } else {
            log.debug("Received PUBLISH message while clientId is not set: {}", publishMsg);
        }

        tbMessageStatsReportClient.reportStats(INCOMING_MSGS);
        tbMessageStatsReportClient.reportInboundTraffic(BytesUtil.getPacketSize(publishMsg));
    }

    private void handleFailedPublish(MqttMessage mqttMessage) {
        if (mqttMessage.fixedHeader() == null || MqttMessageType.PUBLISH != mqttMessage.fixedHeader().messageType()) {
            return;
        }

        DecoderResult decoderResult = mqttMessage.decoderResult();
        if (decoderResult.isFailure() && decoderResult.cause() instanceof TooLongFrameException) {
            tbMessageStatsReportClient.reportStats(INCOMING_MSGS);
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof MqttPublishMessage publishMsg) {
            handlePublishWrite(ctx, publishMsg);
        }

        ctx.write(msg, promise);
    }

    private void handlePublishWrite(ChannelHandlerContext ctx, MqttPublishMessage publishMsg) {
        String clientId = getClientId(ctx);
        if (clientId != null) {
            tbMessageStatsReportClient.reportClientReceiveStats(clientId, NettyMqttConverter.extractPublishQos(publishMsg));
        } else {
            log.debug("Sending PUBLISH message while clientId is not set: {}", publishMsg);
        }

        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        tbMessageStatsReportClient.reportOutBoundTraffic(BytesUtil.getPacketSize(publishMsg));
    }

    private String getClientId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(CLIENT_ID_ATTR).get();
    }

}
