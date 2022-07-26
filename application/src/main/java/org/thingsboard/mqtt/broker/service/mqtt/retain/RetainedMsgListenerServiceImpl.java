/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetainedMsgListenerServiceImpl implements RetainedMsgListenerService {

    private final RetainedMsgService retainedMsgService;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public void init(Map<String, RetainedMsg> retainedMsgMap) {
        log.info("Restoring stored retained messages for {} topics.", retainedMsgMap.size());
        retainedMsgMap.forEach((topic, retainedMsg) -> {
            log.trace("[{}] Restoring retained msg - {}.", topic, retainedMsg);
            retainedMsgService.saveRetainedMsg(topic, retainedMsg);
        });
    }

    @Override
    public void startListening(RetainedMsgConsumer retainedMsgConsumer) {
        retainedMsgConsumer.listen(this::processRetainedMsgUpdate);
    }

    private void processRetainedMsgUpdate(String topic, String serviceId, RetainedMsg retainedMsg) {
        if (serviceInfoProvider.getServiceId().equals(serviceId)) {
            log.trace("[{}] Msg was already processed.", topic);
            return;
        }
        if (retainedMsg == null) {
            log.trace("[{}][{}] Clearing remote retained msg.", serviceId, topic);
            retainedMsgService.clearRetainedMsg(topic);
        } else {
            log.trace("[{}][{}] Saving remote retained msg.", serviceId, topic);
            retainedMsgService.saveRetainedMsg(topic, retainedMsg);
        }
    }
}
