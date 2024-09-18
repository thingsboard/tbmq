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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.mqtt.broker.dao.model.UnauthorizedClientEntity;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnauthorizedClientDaoImpl extends JpaAbstractDaoListeningExecutorService implements UnauthorizedClientDao {

    private final UnauthorizedClientRepository unauthorizedClientRepository;
    private final InsertUnauthorizedClientRepository insertUnauthorizedClientRepository;
    private final DeleteUnauthorizedClientRepository deleteUnauthorizedClientRepository;
    private final InsertUnauthorizedClientQueueConfiguration insertQueueConfiguration;
    private final DeleteUnauthorizedClientQueueConfiguration deleteQueueConfiguration;
    @Autowired(required = false)
    private SqlQueueStatsManager statsManager;

    @Value("${sql.batch_sort:true}")
    private boolean batchSortEnabled;

    private TbSqlBlockingQueuePool<UnauthorizedClientEntity> insertQueue;
    private TbSqlBlockingQueuePool<UnauthorizedClientEntity> deleteQueue;

    @PostConstruct
    public void init() {
        initInsertQueue();
        initDeleteQueue();
    }

    @PreDestroy
    public void destroy() {
        if (insertQueue != null) {
            insertQueue.destroy();
        }
        if (deleteQueue != null) {
            deleteQueue.destroy();
        }
    }

    @Override
    public ListenableFuture<Void> save(UnauthorizedClient unauthorizedClient) {
        return insertQueue.add(new UnauthorizedClientEntity(unauthorizedClient));
    }

    @Override
    public ListenableFuture<Void> remove(UnauthorizedClient unauthorizedClient) {
        return deleteQueue.add(new UnauthorizedClientEntity(unauthorizedClient));
    }

    @Override
    public void deleteById(String clientId) {
        unauthorizedClientRepository.deleteById(clientId);
    }

    @Override
    public UnauthorizedClient findByClientId(String clientId) {
        return DaoUtil.getData(unauthorizedClientRepository.findById(clientId).orElse(null));
    }

    @Override
    public PageData<UnauthorizedClient> findAll(PageLink pageLink) {
        if (log.isTraceEnabled()) {
            log.trace("Trying to find unauthorized clients by pageLink {}", pageLink);
        }
        return DaoUtil.toPageData(unauthorizedClientRepository.findAll(
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public PageData<UnauthorizedClient> findAll(UnauthorizedClientQuery query) {
        if (log.isTraceEnabled()) {
            log.trace("Trying to find unauthorized clients by query {}", query);
        }
        List<Boolean> passwordProvidedList = CollectionUtils.isEmpty(query.getPasswordProvidedList()) ? null : query.getPasswordProvidedList();
        List<Boolean> tslUsedList = CollectionUtils.isEmpty(query.getTlsUsedList()) ? null : query.getTlsUsedList();

        return DaoUtil.toPageData(unauthorizedClientRepository.findAllV2(
                Objects.toString(query.getClientId(), ""),
                Objects.toString(query.getIpAddress(), ""),
                Objects.toString(query.getUsername(), ""),
                Objects.toString(query.getReason(), ""),
                query.getPageLink().getStartTime(),
                query.getPageLink().getEndTime(),
                passwordProvidedList,
                tslUsedList,
                Objects.toString(query.getPageLink().getTextSearch(), ""),
                DaoUtil.toPageable(query.getPageLink())));
    }

    @Override
    public void cleanupUnauthorizedClients(long ttl) {
        unauthorizedClientRepository.cleanupUnauthorizedClients(getExpirationTime(ttl));
    }

    private long getExpirationTime(long ttl) {
        return System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl);
    }

    private void initInsertQueue() {
        TbSqlQueueParams unauthorizedClientParams = TbSqlQueueParams.builder()
                .queueName("InsertUnauthorizedClientQueue")
                .batchSize(insertQueueConfiguration.getBatchSize())
                .maxDelay(insertQueueConfiguration.getBatchMaxDelay())
                .batchSortEnabled(batchSortEnabled)
                .build();

        insertQueue = TbSqlBlockingQueuePool.<UnauthorizedClientEntity>builder()
                .params(unauthorizedClientParams)
                .maxThreads(insertQueueConfiguration.getBatchThreads())
                .queueIndexHashFunction(entity -> entity.getClientId().hashCode())
                .processingFunction(insertUnauthorizedClientRepository::saveOrUpdate)
                .statsManager(statsManager)
                .batchUpdateComparator(Comparator.comparing(UnauthorizedClientEntity::getClientId))
                .build();

        insertQueue.init();
    }

    private void initDeleteQueue() {
        TbSqlQueueParams unauthorizedClientParams = TbSqlQueueParams.builder()
                .queueName("DeleteUnauthorizedClientQueue")
                .batchSize(deleteQueueConfiguration.getBatchSize())
                .maxDelay(deleteQueueConfiguration.getBatchMaxDelay())
                .batchSortEnabled(batchSortEnabled)
                .build();

        deleteQueue = TbSqlBlockingQueuePool.<UnauthorizedClientEntity>builder()
                .params(unauthorizedClientParams)
                .maxThreads(deleteQueueConfiguration.getBatchThreads())
                .queueIndexHashFunction(entity -> entity.getClientId().hashCode())
                .processingFunction(deleteUnauthorizedClientRepository::remove)
                .statsManager(statsManager)
                .batchUpdateComparator(Comparator.comparing(UnauthorizedClientEntity::getClientId))
                .build();

        deleteQueue.init();
    }
}
