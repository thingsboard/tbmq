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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationPublishedMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSessionCtxService;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationPersistedMsgCtxServiceImpl implements ApplicationPersistedMsgCtxService {
    private final ApplicationSessionCtxService sessionCtxService;

    @Override
    public ApplicationPersistedMsgCtx loadPersistedMsgCtx(String clientId) {
        Map<Long, Integer> messagesToResend = sessionCtxService.findApplicationSessionCtx(clientId).stream()
                .flatMap(applicationSessionCtx -> applicationSessionCtx.getPublishedMsgInfos().stream())
                .collect(Collectors.toMap(ApplicationPublishedMsgInfo::getOffset, ApplicationPublishedMsgInfo::getPacketId));
        return new ApplicationPersistedMsgCtx(messagesToResend);
    }

    @Override
    public void saveContext(String clientId, ApplicationPackProcessingContext processingContext) {
        if (processingContext == null) {
            return;
        }
        long lastCommittedOffset = processingContext.getLastCommittedOffset();
        Collection<ApplicationPublishedMsgInfo> publishedMsgInfos = processingContext.getPendingMap().entrySet().stream()
                .filter(entry -> entry.getValue().getOffset() > lastCommittedOffset)
                .map(entry -> ApplicationPublishedMsgInfo.builder()
                        .packetId(entry.getKey())
                        .offset(entry.getValue().getOffset())
                        .build())
                .collect(Collectors.toList());
        ApplicationSessionCtx sessionCtx = ApplicationSessionCtx.builder()
                .clientId(clientId)
                .publishedMsgInfos(publishedMsgInfos)
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
