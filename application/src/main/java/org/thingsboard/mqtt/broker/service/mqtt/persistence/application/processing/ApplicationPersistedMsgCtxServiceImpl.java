/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSessionCtxService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationPersistedMsgCtxServiceImpl implements ApplicationPersistedMsgCtxService {
    private final ApplicationSessionCtxService sessionCtxService;

    @Override
    public ApplicationPersistedMsgCtx loadPersistedMsgCtx(String clientId) {
        ApplicationSessionCtx applicationSessionCtx = sessionCtxService.findApplicationSessionCtx(clientId).orElse(null);
        if (applicationSessionCtx == null) {
            return new ApplicationPersistedMsgCtx(Collections.emptyMap(), Collections.emptyMap());
        }
        Map<Long, Integer> publishMsgIds = applicationSessionCtx.getPublishMsgInfos().stream()
                .collect(Collectors.toMap(ApplicationMsgInfo::getOffset, ApplicationMsgInfo::getPacketId));
        Map<Long, Integer> pubRelMsgIds = applicationSessionCtx.getPubRelMsgInfos().stream()
                .collect(Collectors.toMap(ApplicationMsgInfo::getOffset, ApplicationMsgInfo::getPacketId));
        return new ApplicationPersistedMsgCtx(publishMsgIds, pubRelMsgIds);
    }

    @Override
    public void saveContext(String clientId, ApplicationPackProcessingContext processingContext) {
        if (processingContext == null) {
            log.debug("[{}] No pack processing context found.", clientId);
            return;
        }
        long lastCommittedOffset = processingContext.getLastCommittedOffset();
        Collection<ApplicationMsgInfo> publishMsgInfos = processingContext.getPublishPendingMsgMap().values().stream()
                .filter(publishMsg -> publishMsg.getPacketOffset() > lastCommittedOffset)
                .map(publishMsg -> ApplicationMsgInfo.builder()
                        .packetId(publishMsg.getPacketId())
                        .offset(publishMsg.getPacketOffset())
                        .build())
                .collect(Collectors.toList());
        Stream<PersistedPubRelMsg> pubRelMsgStream = Streams.concat(processingContext.getPubRelPendingMsgMap().values().stream(),
                processingContext.getNewPubRelPackets().stream());
        Collection<ApplicationMsgInfo> pubRelMsgInfos = pubRelMsgStream
                .map(publishMsg -> ApplicationMsgInfo.builder()
                        .packetId(publishMsg.getPacketId())
                        .offset(publishMsg.getPacketOffset())
                        .build())
                .collect(Collectors.toList());
        ApplicationSessionCtx sessionCtx = ApplicationSessionCtx.builder()
                .clientId(clientId)
                .publishMsgInfos(publishMsgInfos)
                .pubRelMsgInfos(pubRelMsgInfos)
                .build();
        log.trace("[{}] Saving application session context - {}.", clientId, sessionCtx);
        sessionCtxService.saveApplicationSessionCtx(sessionCtx);
    }

    @Override
    public void clearContext(String clientId) {
        log.trace("[{}] Clearing application session context.", clientId);
        sessionCtxService.deleteApplicationSessionCtx(clientId);
    }
}
