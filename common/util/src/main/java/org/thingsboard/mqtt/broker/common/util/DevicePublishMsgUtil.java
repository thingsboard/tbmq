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
package org.thingsboard.mqtt.broker.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.csv.CSVRecord;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

@Slf4j
@Deprecated(forRemoval = true, since = "2.0.0")
public class DevicePublishMsgUtil {

    public static DevicePublishMsg fromCsvRecord(CSVRecord record, int defaultTtl) throws DecoderException {
        var devicePublishMsg = new DevicePublishMsg();
        // non-null fields
        String clientId = record.get("client_id");
        devicePublishMsg.setClientId(clientId);
        devicePublishMsg.setTopic(record.get("topic"));
        devicePublishMsg.setTime(Long.parseLong(record.get("time")));

        devicePublishMsg.setPacketId(Integer.parseInt(record.get("packet_id")));
        devicePublishMsg.setPacketType(PersistedPacketType.valueOf(record.get("packet_type")));

        // non-null fields
        devicePublishMsg.setQos(Integer.parseInt(record.get("qos")));
        devicePublishMsg.setPayload(getPayload(record, clientId));

        devicePublishMsg.setUserProperties(getUserProperties(record));
        devicePublishMsg.setMsgExpiryInterval(getMsgExpiryInterval(record, defaultTtl));
        devicePublishMsg.setRetained(isRetain(record));
        devicePublishMsg.setPayloadFormatIndicator(getPayloadFormatIndicator(record));
        devicePublishMsg.setContentType(getContentType(record));
        devicePublishMsg.setResponseTopic(getResponseTopic(record));
        devicePublishMsg.setCorrelationData(getCorrelationData(record, clientId));
        return devicePublishMsg;
    }

    // CSV import helper methods

    private static byte[] getPayload(CSVRecord record, String clientId) {
        try {
            return Hex.decodeHex(record.get("payload").substring(2)); // substring \x
        } catch (DecoderException e) {
            log.error("Failed to decode payload for clientId: {}", clientId, e);
            return null;
        }
    }

    private static UserProperties getUserProperties(CSVRecord record) {
        String userProperties = record.get("user_properties");
        return StringUtils.isNotBlank(userProperties) ? JacksonUtil.fromString(userProperties, UserProperties.class) : null;
    }

    private static boolean isRetain(CSVRecord record) {
        return "t".equalsIgnoreCase(record.get("retain"));
    }

    private static int getMsgExpiryInterval(CSVRecord record, int defaultTtl) {
        String msgExpiryInterval = record.get("msg_expiry_interval");
        return StringUtils.isNotBlank(msgExpiryInterval) ? Integer.parseInt(msgExpiryInterval) : defaultTtl;
    }

    private static Integer getPayloadFormatIndicator(CSVRecord record) {
        String payloadFormatIndicator = record.get("payload_format_indicator");
        return StringUtils.isNotBlank(payloadFormatIndicator) ? Integer.parseInt(payloadFormatIndicator) : null;
    }

    private static String getContentType(CSVRecord record) {
        String contentType = record.get("content_type");
        return StringUtils.isNotBlank(contentType) ? contentType : null;
    }

    private static String getResponseTopic(CSVRecord record) {
        String responseTopic = record.get("response_topic");
        return StringUtils.isNotBlank(responseTopic) ? responseTopic : null;
    }

    private static byte[] getCorrelationData(CSVRecord record, String clientId) {
        String correlationData = record.get("correlation_data");
        if (StringUtils.isBlank(correlationData)) {
            log.debug("Correlation data is missing for clientId: {}", clientId);
            return null;
        }
        try {
            return Hex.decodeHex(correlationData.substring(2)); // substring \x
        } catch (DecoderException e) {
            log.error("Failed to parse correlation data for clientId: {}", clientId, e);
            return null;
        }
    }

}
