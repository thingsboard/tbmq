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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;

/**
 * A single-node broker has no remote node to deliver to, so a request for remote delivery is a defect
 * in the caller (or a session stored under another service id). It is never reported as delivered and is
 * logged as an error. As in the cluster publisher, only a BASIC message is counted as dropped: a PERSISTENT
 * one is already in the device store and is delivered when the client reconnects.
 */
@Slf4j
@Service
@StandaloneComponent
@RequiredArgsConstructor
class StandaloneDownLinkQueuePublisher implements DownLinkQueuePublisher {

    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Override
    public void publishBasicMsg(String targetServiceId, String clientId, PublishMsgProto msg) {
        log.error("[{}] BASIC msg dropped: remote delivery requested on a single-node broker, target service {}.",
                clientId, targetServiceId);
        tbMessageStatsReportClient.reportDroppedMsgs();
    }

    @Override
    public void publishPersistentMsg(String targetServiceId, String clientId, DevicePublishMsg msg) {
        log.error("[{}] PERSISTENT msg not delivered: remote delivery requested on a single-node broker, " +
                "target service {}. It stays stored until the client reconnects.", clientId, targetServiceId);
    }

}
