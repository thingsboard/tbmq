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
package org.thingsboard.mqtt.broker.service;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationRequestProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto.Builder;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatistics;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.util.TbPlatformIntegrationUtil;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbmqOrIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.integration.IntegrationUplinkNotificationsService;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationDownlinkQueueProvider;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;
import org.thingsboard.mqtt.broker.service.state.IntegrationState;
import org.thingsboard.mqtt.broker.service.state.ValidationTask;
import org.thingsboard.mqtt.broker.service.state.ValidationTaskType;

import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.DELETED;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.FAILED;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.REINIT;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.STARTED;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.STOPPED;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ_INTEGRATION_EXECUTOR;

@Slf4j
@Service
@RequiredArgsConstructor
@Order
@TbmqOrIntegrationExecutorComponent
public class IntegrationManagerServiceImpl implements IntegrationManagerService {

    private final ConcurrentMap<UUID, IntegrationState> integrations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ValidationTask> pendingValidationTasks = new ConcurrentHashMap<>();
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private final @Lazy EventStorageService eventStorageService;
    private final @Lazy IntegrationUplinkNotificationsService integrationUplinkNotificationsService;
    private final @Lazy ServiceInfoProvider serviceInfoProvider;
    private final @Lazy IntegrationDownlinkQueueProvider ieDownlinkQueueProvider;
    private final @Lazy IntegrationContextProvider integrationContextProvider;
    private final @Lazy Optional<IntegrationStatisticsService> integrationStatisticsService;

    @Value("${integrations.reinit.enabled:false}")
    private boolean reInitEnabled;
    @Value("${integrations.reinit.frequency:300000}")
    private long reInitFrequency;

    @Value("${integrations.statistics.enabled:false}")
    private boolean statisticsEnabled;
    @Value("${integrations.statistics.persist-frequency:3600000}")
    private long statisticsPersistFrequency;

    @Getter
    @Value("${integrations.init.connection-check-api-request-timeout-sec:20}")
    private int integrationConnectionCheckApiRequestTimeoutSec;
    @Value("${integrations.allow-local-network-hosts:true}")
    private boolean allowLocalNetworkHosts;

    @Value("${integrations.manage.lifecycle-threads-count:4}")
    private int lifecycleThreadsCount;
    @Value("${integrations.manage.command-threads-count:4}")
    private int commandThreadsCount;

    @Value("${integrations.destroy.graceful-timeout-ms:1000}")
    private int gracefulShutdownTimeoutMs;
    @Value("${integrations.destroy.count:10}")
    private int gracefulShutdownIterations;
    @Value("${integrations.destroy.forced-shutdown-timeout-ms:15000}")
    private int forcedShutdownTimeoutMs;

    private ScheduledExecutorService lifecycleExecutorService;
    private ScheduledExecutorService reInitExecutorService;
    private ScheduledExecutorService statisticsExecutorService;
    private ExecutorService commandExecutorService;

    @PostConstruct
    public void init() {
        log.info("Initializing IntegrationManagerService");
        lifecycleExecutorService = ThingsBoardExecutors.newScheduledThreadPool(lifecycleThreadsCount, "ie-lifecycle");
        lifecycleExecutorService.scheduleWithFixedDelay(this::cleanupPendingValidationTasks, 1, 1, TimeUnit.MINUTES);
        if (reInitEnabled) {
            reInitExecutorService = ThingsBoardExecutors.newSingleScheduledThreadPool("ie-reinit");
            reInitExecutorService.scheduleAtFixedRate(this::reInitIntegrations, reInitFrequency, reInitFrequency, TimeUnit.MILLISECONDS);
        }
        if (statisticsEnabled) {
            statisticsExecutorService = ThingsBoardExecutors.newSingleScheduledThreadPool("ie-stats");
            statisticsExecutorService.scheduleAtFixedRate(this::persistStatistics, statisticsPersistFrequency, statisticsPersistFrequency, TimeUnit.MILLISECONDS);
        }
        if (TBMQ_INTEGRATION_EXECUTOR.equals(ServiceType.of(serviceInfoProvider.getServiceType()))) {
            commandExecutorService = ThingsBoardExecutors.newWorkStealingPool(commandThreadsCount, "ie-commands");
            integrationStatisticsService.ifPresent(IntegrationStatisticsService::reset);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Destroying IntegrationManagerService");
        integrations.forEach((uuid, integrationState) -> handleStopIntegrationLifecycleMsg(integrationState.getLifecycleMsg()));
        try {
            boolean await = stopLatch.await(forcedShutdownTimeoutMs, TimeUnit.MILLISECONDS);
            log.info(await ? "IE manager service graceful-stop has ended!" : "IE manager service graceful-stop has failed. Executing forceful shutdown!");
        } catch (InterruptedException e) {
            log.error("IE manager service graceful stop was interrupted", e);
        }
        if (lifecycleExecutorService != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(lifecycleExecutorService, "IE lifecycle service");
        }
        if (reInitExecutorService != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(reInitExecutorService, "IE reinit service");
        }
        if (commandExecutorService != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(commandExecutorService, "IE commands service");
        }
        if (statisticsEnabled) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(statisticsExecutorService, "IE stats service");
        }
    }

    @Override
    public void handleIntegrationLifecycleMsg(IntegrationLifecycleMsg integrationLifecycleMsg) {
        log.trace("process handleIntegrationLifecycleMsg {}", integrationLifecycleMsg);
        scheduleIntegrationEvent(integrationLifecycleMsg, integrationLifecycleMsg.getEvent());
    }

    @Override
    public void handleStopIntegrationLifecycleMsg(IntegrationLifecycleMsg integrationLifecycleMsg) {
        log.trace("process handleStopIntegrationLifecycleMsg {}", integrationLifecycleMsg);
        scheduleIntegrationEvent(integrationLifecycleMsg, STOPPED);
    }

    @Override
    public void handleValidationRequest(IntegrationValidationRequestProto validationRequestMsg, TbCallback callback) {
        log.debug("submitting validation request: {}", validationRequestMsg);
        if (isExpired(validationRequestMsg, callback)) {
            return;
        }

        commandExecutorService.submit(() -> processValidationRequest(validationRequestMsg, callback));
    }

    private void processValidationRequest(IntegrationValidationRequestProto validationRequestMsg, TbCallback callback) {
        log.debug("Starting validation request: {}", validationRequestMsg);
        if (isExpired(validationRequestMsg, callback)) {
            return;
        }

        UUID requestId = new UUID(validationRequestMsg.getIdMSB(), validationRequestMsg.getIdLSB());
        var response = buildValidationResponse(requestId);

        BasicCallback validationCallback = CallbackUtil.createCallback(() -> {
            sendValidationResponse(response, validationRequestMsg, requestId);
            callback.onSuccess();
        }, throwable -> {
            response.setError(ByteString.copyFrom(JavaSerDesUtil.encode(throwable.getMessage())));
            sendValidationResponse(response, validationRequestMsg, requestId);
            callback.onFailure(throwable);
        });

        try {
            ValidationTaskType validationTaskType = ValidationTaskType.valueOf(validationRequestMsg.getType());
            Integration configuration = IntegrationProtoConverter.fromProto(validationRequestMsg.getConfiguration());
            doValidateLocally(validationTaskType, configuration, validationCallback);
            log.trace("[{}] Processed the validation request for integration: {}", requestId, configuration);
        } catch (Exception e) {
            log.trace("[{}][{}] Integration validation failed", validationRequestMsg.getType(), requestId, e);
            validationCallback.onFailure(e);
        }
    }

    private void sendValidationResponse(Builder response, IntegrationValidationRequestProto validationRequestMsg, UUID requestId) {
        integrationUplinkNotificationsService.sendNotification(response.build(), validationRequestMsg.getServiceId(), requestId);
    }

    private Builder buildValidationResponse(UUID requestId) {
        return IntegrationValidationResponseProto.newBuilder()
                .setIdMSB(requestId.getMostSignificantBits())
                .setIdLSB(requestId.getLeastSignificantBits());
    }

    boolean isExpired(IntegrationValidationRequestProto validationRequestMsg, TbCallback callback) {
        if (validationRequestMsg.getDeadline() > 0 && validationRequestMsg.getDeadline() <= System.currentTimeMillis()) {
            UUID requestId = new UUID(validationRequestMsg.getIdMSB(), validationRequestMsg.getIdLSB());
            log.debug("[{}][{}] Integration validation expired: {}", validationRequestMsg.getType(), requestId, validationRequestMsg.getDeadline());
            callback.onFailure(new TimeoutException("Integration validation expired"));
            return true;
        }
        return false;
    }

    private void doValidateLocally(ValidationTaskType validationTaskType, Integration configuration, BasicCallback callback) throws Exception {
        IntegrationContext context = integrationContextProvider.buildIntegrationContext(configuration, callback);

        TbPlatformIntegration integration = createPlatformIntegration(context.getLifecycleMsg().getType());
        switch (validationTaskType) {
            case VALIDATE:
                integration.validateConfiguration(context.getLifecycleMsg(), allowLocalNetworkHosts);
                callback.onSuccess();
                break;
            case CHECK_CONNECTION:
                integration.checkConnection(configuration, context);
        }
    }

    @Override
    public void proceedGracefulShutdown() {
        log.info("Proceed graceful shutdown for {} integrations: {} iterations, {}ms",
                integrations.size(), gracefulShutdownIterations, gracefulShutdownTimeoutMs);
        lifecycleExecutorService.submit(() -> {
            for (int i = 0; i < gracefulShutdownIterations; i++) {
                if (!integrations.isEmpty()) {
                    try {
                        Thread.sleep(gracefulShutdownTimeoutMs);
                    } catch (InterruptedException e) {
                        log.error("Failed to sleep during graceful shutdown", e);
                    }
                } else {
                    break;
                }
            }
            this.stopLatch.countDown();
        });
    }

    @Override
    public ListenableFuture<Void> validateIntegrationConfiguration(Integration configuration) {
        return validateConfiguration(configuration, ValidationTaskType.VALIDATE);
    }

    @Override
    public ListenableFuture<Void> checkIntegrationConnection(Integration configuration) {
        return validateConfiguration(configuration, ValidationTaskType.CHECK_CONNECTION);
    }

    private ListenableFuture<Void> validateConfiguration(Integration configuration, ValidationTaskType validationTaskType) {
        log.trace("[{}] process validateConfiguration {}", validationTaskType, configuration);
        if (StringUtils.isEmpty(configuration.getName())) {
            return Futures.immediateFailedFuture(new DataValidationException("Integration name should be specified!"));
        }
        if (configuration.getType() == null) {
            return Futures.immediateFailedFuture(new DataValidationException("Integration type should be specified!"));
        }
        try {
            if (configuration.getId() == null) {
                configuration = new Integration(configuration);
                configuration.setId(UUID.randomUUID());
            }

            ValidationTask task = new ValidationTask(validationTaskType, configuration);
            pendingValidationTasks.put(task.getUuid(), task);

            var producer = ieDownlinkQueueProvider.getIeDownlinkProducer(configuration.getType());

            IntegrationValidationRequestProto requestProto = IntegrationValidationRequestProto.newBuilder()
                    .setIdMSB(task.getUuid().getMostSignificantBits())
                    .setIdLSB(task.getUuid().getLeastSignificantBits())
                    .setServiceId(serviceInfoProvider.getServiceId())
                    .setConfiguration(IntegrationProtoConverter.toProto(configuration))
                    .setType(validationTaskType.name())
                    .setTimestamp(System.currentTimeMillis())
                    .setDeadline(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(this.getIntegrationConnectionCheckApiRequestTimeoutSec()))
                    .build();

            producer.send(new TbProtoQueueMsg<>(configuration.getId(), DownlinkIntegrationMsgProto.newBuilder()
                    .setValidationRequestMsg(requestProto).build()), new TbQueueCallback() {
                @Override
                public void onSuccess(TbQueueMsgMetadata metadata) {
                    log.trace("[{}] Request to validate the configuration is sent!", task);
                }

                @Override
                public void onFailure(Throwable t) {
                    pendingValidationTasks.remove(task.getUuid());
                    task.getFuture().setException(t);
                }
            });
            return task.getFuture();
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public void handleValidationResponse(IntegrationValidationResponseProto validationResponseMsg, TbCallback callback) {
        UUID requestId = new UUID(validationResponseMsg.getIdMSB(), validationResponseMsg.getIdLSB());
        log.trace("[{}] process handleValidationResponse", requestId);
        ValidationTask validationTask = pendingValidationTasks.remove(requestId);
        if (validationTask != null) {
            SettableFuture<Void> future = validationTask.getFuture();
            if (validationResponseMsg.hasError()) {
                String errorMsg = JavaSerDesUtil.decode(validationResponseMsg.getError().toByteArray());
                future.setException(new RuntimeException(errorMsg));
            } else {
                future.set(null);
            }
        } else {
            log.debug("[{}] Validation task was removed by the expiration time", requestId);
        }
    }

    private void cleanupPendingValidationTasks() {
        long expTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
        pendingValidationTasks.values().stream().filter(task -> task.getTs() < expTime).map(ValidationTask::getUuid).forEach(pendingValidationTasks::remove);
    }

    private TbPlatformIntegration createPlatformIntegration(IntegrationType type) throws Exception {
        return TbPlatformIntegrationUtil.createPlatformIntegration(type);
    }

    private void scheduleIntegrationEvent(IntegrationLifecycleMsg integrationLifecycleMsg, ComponentLifecycleEvent event) {
        UUID id = integrationLifecycleMsg.getIntegrationId();
        IntegrationState state = switch (event) {
            case CREATED, UPDATED, REINIT -> integrations.computeIfAbsent(id, __ -> new IntegrationState(id));
            default -> integrations.get(id);
        };
        if (state != null) {
            state.getUpdateQueue().add(event);
            log.debug("[{}] Scheduling new event: {}", id, event);
            lifecycleExecutorService.submit(() -> tryUpdate(integrationLifecycleMsg));
        } else {
            log.info("[{}] Ignoring new event: {}", id, event);
        }
    }

    private void tryUpdate(IntegrationLifecycleMsg lifecycleMsg) {
        UUID id = lifecycleMsg.getIntegrationId();
        IntegrationState state = integrations.get(id);
        if (state == null) {
            log.debug("[{}] Skip processing of update due to missing state. Probably integration was already deleted.", id);
            return;
        }
        boolean locked = state.getUpdateLock().tryLock();
        if (locked) {
            boolean success = true;
            var old = state.getCurrentState();
            try {
                update(state, lifecycleMsg);
            } catch (Throwable e) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Failed to update state", id, e);
                } else {
                    log.warn("[{}] Failed to update state due to: {}. Enable debug level to get more details.", id, e.getMessage());
                }
                success = false;
            } finally {
                state.getUpdateLock().unlock();
            }
            onIntegrationStateUpdate(state, old, success);
        } else {
            lifecycleExecutorService.schedule(() -> tryUpdate(lifecycleMsg), 10, TimeUnit.SECONDS);
        }
    }

    private void update(IntegrationState state, IntegrationLifecycleMsg lifecycleMsg) throws Exception {
        Queue<ComponentLifecycleEvent> stateQueue = state.getUpdateQueue();
        ComponentLifecycleEvent pendingEvent = null;
        while (!stateQueue.isEmpty()) {
            var update = stateQueue.poll();
            if (!DELETED.equals(pendingEvent)) {
                pendingEvent = update;
            }
        }
        if (pendingEvent != null) {
            switch (pendingEvent) {
                case CREATED:
                case UPDATED:
                case REINIT:
                    processUpdateEvent(state, lifecycleMsg);
                    break;
                case STOPPED:
                case DELETED:
                    persistCurrentStatistics(state);
                    processStop(state, pendingEvent);
                    break;
            }
            log.debug("[{}] Going to process new event: {}", state.getIntegrationId(), pendingEvent);
        }
    }

    private void processUpdateEvent(IntegrationState state, IntegrationLifecycleMsg lifecycleMsg) throws Exception {
        state.setLifecycleMsg(lifecycleMsg);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Going to update the integration: {}", lifecycleMsg.getIntegrationId(), lifecycleMsg.getConfiguration());
        } else {
            log.info("[{}] Going to update the integration.", lifecycleMsg.getIntegrationId());
        }
        if (state.getIntegration() == null) {
            if (!lifecycleMsg.isEnabled()) {
                log.info("[{}][{}] The integration is disabled!", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName());
                return;
            }
            IntegrationContext context = integrationContextProvider.buildIntegrationContext(lifecycleMsg);
            TbPlatformIntegration integration = createPlatformIntegration(lifecycleMsg.getType());
            integration.validateConfiguration(lifecycleMsg, allowLocalNetworkHosts);
            state.setIntegration(integration);
            try {
                integration.init(new TbIntegrationInitParams(context, lifecycleMsg));
                eventStorageService.persistLifecycleEvent(lifecycleMsg.getIntegrationId(), STARTED, null);
                state.setCurrentState(STARTED);
            } catch (Exception e) {
                state.setCurrentState(FAILED);
                eventStorageService.persistLifecycleEvent(lifecycleMsg.getIntegrationId(), FAILED, e);
                throw handleException(e);
            }
        } else {
            IntegrationContext context = integrationContextProvider.buildIntegrationContext(lifecycleMsg);
            try {
                if (lifecycleMsg.isEnabled()) {
                    state.getIntegration().update(new TbIntegrationInitParams(context, lifecycleMsg));
                    eventStorageService.persistLifecycleEvent(lifecycleMsg.getIntegrationId(), STARTED, null);
                    state.setCurrentState(STARTED);
                } else {
                    persistCurrentStatistics(state);
                    processStop(state, STOPPED);
                }
            } catch (Exception e) {
                state.setCurrentState(FAILED);
                eventStorageService.persistLifecycleEvent(lifecycleMsg.getIntegrationId(), FAILED, e);
                throw handleException(e);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Updated the integration successfully: {}", lifecycleMsg.getIntegrationId(), lifecycleMsg.getConfiguration());
        } else {
            log.info("[{}] Updated the integration successfully.", lifecycleMsg.getIntegrationId());
        }
    }

    private void processStop(IntegrationState state, ComponentLifecycleEvent event) {
        if (state != null) {
            integrations.remove(state.getIntegrationId());
            if (!state.getCurrentState().equals(STARTED)) {
                log.info("[{}] Received event {} when current state is {}", state.getIntegrationId(), event, state.getCurrentState());
            }
            TbPlatformIntegration integration = state.getIntegration();
            if (integration != null) {
                state.setCurrentState(event);
                try {
                    destroy(event, integration);
                    eventStorageService.persistLifecycleEvent(state.getIntegrationId(), STOPPED, null);
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Failed to destroy the integration: {}", state.getIntegrationId(), state.getLifecycleMsg(), e);
                    } else {
                        log.warn("[{}] Failed to destroy the integration: ", state.getIntegrationId(), e);
                    }
                    eventStorageService.persistLifecycleEvent(state.getIntegrationId(), FAILED, e);
                    throw handleException(e);
                }
            }
        }
    }

    private void destroy(ComponentLifecycleEvent event, TbPlatformIntegration integration) {
        if (DELETED.equals(event)) {
            integration.destroyAndClearData();
        } else {
            integration.destroy();
        }
    }

    private RuntimeException handleException(Exception e) {
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new RuntimeException(e);
        }
    }

    private void reInitIntegrations() {
        integrations.values().forEach(state -> {
            if (state.getLifecycleMsg() != null && state.getCurrentState() != null && ComponentLifecycleEvent.FAILED.equals(state.getCurrentState())) {
                if (state.getUpdateLock().tryLock()) {
                    boolean success = true;
                    try {
                        eventStorageService.persistLifecycleEvent(state.getIntegrationId(), REINIT, null);
                        processUpdateEvent(state, state.getLifecycleMsg());
                    } catch (Exception e) {
                        log.warn("[{}] Failed to re-initialize the integration", state.getIntegrationId(), e);
                        success = false;
                    } finally {
                        state.getUpdateLock().unlock();
                    }
                    onIntegrationStateUpdate(state, ComponentLifecycleEvent.FAILED, success);
                }
            }
        });
    }

    private void persistStatistics() {
        integrations.forEach((id, integration) -> doPersistStatistics(integration, false));
    }

    private void persistCurrentStatistics(IntegrationState integrationState) {
        if (statisticsEnabled) {
            doPersistStatistics(integrationState, true);
        }
    }

    private void doPersistStatistics(IntegrationState integrationState, boolean skipEmptyStatistics) {
        if (!STARTED.equals(integrationState.getCurrentState())) {
            log.debug("[{}] Can't persist statistics, integration has not started yet!}", integrationState.getIntegrationId());
            return;
        }
        TbPlatformIntegration integration = integrationState.getIntegration();
        if (integration == null) {
            return;
        }
        IntegrationStatistics statistics = integration.popStatistics();
        if (skipEmptyStatistics && statistics.isEmpty()) {
            return;
        }
        try {
            eventStorageService.persistStatistics(integrationState.getIntegrationId(), statistics);
        } catch (Exception e) {
            log.warn("[{}] Failed to persist statistics: {}", integrationState.getIntegrationId(), statistics, e);
        }
    }

    private void onIntegrationStateUpdate(IntegrationState state, ComponentLifecycleEvent oldState, boolean success) {
        try {
            if (state.getLifecycleMsg() != null) {
                var integrationType = state.getLifecycleMsg().getType();
                if (integrationType != null && state.getCurrentState() != null) {
                    integrationStatisticsService.ifPresent(svc -> svc.onIntegrationStateUpdate(integrationType, state.getCurrentState(), success));
                }
                if (oldState == null || !oldState.equals(state.getCurrentState())) {
                    int startedCount = (int) integrations.values()
                            .stream()
                            .filter(i -> i.getCurrentState() != null)
                            .filter(i -> i.getLifecycleMsg() != null && i.getLifecycleMsg().getType().equals(integrationType))
                            .filter(i -> STARTED.equals(i.getCurrentState()))
                            .count();

                    int failedCount = (int) integrations.values()
                            .stream()
                            .filter(i -> i.getCurrentState() != null)
                            .filter(i -> i.getLifecycleMsg() != null && i.getLifecycleMsg().getType().equals(integrationType))
                            .filter(i -> FAILED.equals(i.getCurrentState()))
                            .count();

                    integrationStatisticsService.ifPresent(svc -> svc.onIntegrationsCountUpdate(integrationType, startedCount, failedCount));
                }
            }
        } catch (Exception e) {
            log.warn("[{}][{}][{}] Failed to process integration state update", state.getIntegrationId(), oldState, success, e);
        }
    }

}
