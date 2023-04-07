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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSessionCtxService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
        if (log.isTraceEnabled()) {
            log.trace("[{}] Loading persisted messages context.", clientId);
        }
        ApplicationSessionCtx applicationSessionCtx = findApplicationSessionCtx(clientId);
        if (applicationSessionCtx == null) {
            return new ApplicationPersistedMsgCtx(Collections.emptyMap(), Collections.emptyMap());
        }
        Map<Long, Integer> publishMsgIds = getPendingMsgsFromApplicationCtx(applicationSessionCtx.getPublishMsgInfos());
        Map<Long, Integer> pubRelMsgIds = getPendingMsgsFromApplicationCtx(applicationSessionCtx.getPubRelMsgInfos());
        return new ApplicationPersistedMsgCtx(publishMsgIds, pubRelMsgIds);
    }

    private ApplicationSessionCtx findApplicationSessionCtx(String clientId) {
        return sessionCtxService.findApplicationSessionCtx(clientId).orElse(null);
    }

    private Map<Long, Integer> getPendingMsgsFromApplicationCtx(Collection<ApplicationMsgInfo> applicationSessionCtx) {
        return applicationSessionCtx.stream()
                .collect(Collectors.toMap(ApplicationMsgInfo::getOffset, ApplicationMsgInfo::getPacketId));
    }

    @Override
    public void saveContext(String clientId, ApplicationPackProcessingCtx processingContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing save application session context.", clientId);
        }
        if (processingContext == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No pack processing context found.", clientId);
            }
            return;
        }
        Stream<PersistedPublishMsg> publishMsgStream = toPublishMsgStream(processingContext);
        Collection<ApplicationMsgInfo> publishMsgInfos = toPublishMsgInfos(publishMsgStream);

        Stream<PersistedPubRelMsg> pubRelMsgStream = toPubRelMsgStream(processingContext);
        Collection<ApplicationMsgInfo> pubRelMsgInfos = toPubRelMsgInfos(pubRelMsgStream);

        ApplicationSessionCtx sessionCtx = buildApplicationSessionCtx(clientId, publishMsgInfos, pubRelMsgInfos);
        if (log.isTraceEnabled()) {
            log.trace("[{}] Saving application session context - {}.", clientId, sessionCtx);
        }
        sessionCtxService.saveApplicationSessionCtx(sessionCtx);
    }

    private Stream<PersistedPublishMsg> toPublishMsgStream(ApplicationPackProcessingCtx processingContext) {
        return processingContext.getPublishPendingMsgMap().values().stream();
    }

    private Stream<PersistedPubRelMsg> toPubRelMsgStream(ApplicationPackProcessingCtx processingContext) {
        return Streams.concat(
                processingContext.getPubRelPendingMsgMap().values().stream(),
                processingContext.getPubRelMsgCtx().getPubRelMessagesToDeliver().stream()
        );
    }

    private Collection<ApplicationMsgInfo> toPublishMsgInfos(Stream<PersistedPublishMsg> publishMsgStream) {
        return publishMsgStream
                .map(publishMsg -> buildApplicationMsgInfo(publishMsg.getPacketId(), publishMsg.getPacketOffset()))
                .collect(Collectors.toList());
    }

    private List<ApplicationMsgInfo> toPubRelMsgInfos(Stream<PersistedPubRelMsg> pubRelMsgStream) {
        return pubRelMsgStream
                .map(pubRelMsg -> buildApplicationMsgInfo(pubRelMsg.getPacketId(), pubRelMsg.getPacketOffset()))
                .collect(Collectors.toList());
    }

    private ApplicationMsgInfo buildApplicationMsgInfo(int packetId, long packetOffset) {
        return ApplicationMsgInfo.builder()
                .packetId(packetId)
                .offset(packetOffset)
                .build();
    }

    private ApplicationSessionCtx buildApplicationSessionCtx(String clientId,
                                                             Collection<ApplicationMsgInfo> publishMsgInfos,
                                                             Collection<ApplicationMsgInfo> pubRelMsgInfos) {
        return ApplicationSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(System.currentTimeMillis())
                .publishMsgInfos(publishMsgInfos)
                .pubRelMsgInfos(pubRelMsgInfos)
                .build();
    }

    @Override
    public void clearContext(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Clearing application session context.", clientId);
        }
        try {
            sessionCtxService.deleteApplicationSessionCtx(clientId);
        } catch (EmptyResultDataAccessException noDataException) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No session for clientId.", clientId);
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to clear application session context. Reason - {}.", clientId, e.getMessage());
        }
    }
}
