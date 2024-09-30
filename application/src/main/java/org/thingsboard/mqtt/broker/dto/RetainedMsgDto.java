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
package org.thingsboard.mqtt.broker.dto;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
public class RetainedMsgDto {

    private final String topic;
    private final String payload;
    private final int qos;
    private final long createdTime;
    private final UserProperties userProperties;

    public static RetainedMsgDto newInstance(RetainedMsg retainedMsg) {
        return new RetainedMsgDto(
                retainedMsg.getTopic(),
                new String(retainedMsg.getPayload(), StandardCharsets.UTF_8),
                retainedMsg.getQos(),
                retainedMsg.getCreatedTime(),
                UserProperties.newInstance(retainedMsg.getProperties())
        );
    }

    public static Comparator<RetainedMsgDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "topic" -> getComparatorBy(sortOrder, RetainedMsgDto::getTopic);
            case "qos" -> getComparatorBy(sortOrder, RetainedMsgDto::getQos);
            case "createdTime" -> getComparatorBy(sortOrder, RetainedMsgDto::getCreatedTime);
            default -> null;
        };
    }

}
