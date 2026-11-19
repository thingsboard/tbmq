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
package org.thingsboard.mqtt.broker.dao;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.thingsboard.mqtt.broker.dao.model.BaseEntity;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractDao<E extends BaseEntity<D>, D>
        extends JpaAbstractDaoListeningExecutorService
        implements Dao<D> {

    private TransactionTemplate transactionTemplate;

    @Autowired
    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    protected abstract Class<E> getEntityClass();

    protected abstract CrudRepository<E, UUID> getCrudRepository();

    @Override
    public D save(D domain) {
        return inTransaction(() -> saveInTransaction(domain));
    }

    /**
     * Runs {@code body} in a transaction of this DAO's {@link TransactionTemplate} (joining the caller's, if any) and
     * translates a constraint violation, also one raised at the commit, as {@link #save} does.
     */
    protected <T> T inTransaction(Supplier<T> body) {
        try {
            return transactionTemplate.execute(status -> body.get());
        } catch (DataIntegrityViolationException e) {
            throw DbExceptionUtil.translate(e);
        }
    }

    private D saveInTransaction(D domain) {
        E entity;
        try {
            entity = getEntityClass().getConstructor(domain.getClass()).newInstance(domain);
        } catch (Exception e) {
            log.error("Can't create entity for domain object {}", domain, e);
            throw new IllegalArgumentException("Can't create entity for domain object {" + domain + "}", e);
        }
        log.debug("Saving entity {}", entity);
        if (entity.getId() == null) {
            UUID uuid = UUID.randomUUID();
            entity.setId(uuid);
            entity.setCreatedTime(System.currentTimeMillis());
        }
        entity = getCrudRepository().save(entity);
        return DaoUtil.getData(entity);
    }

    @Override
    public D findById(UUID key) {
        log.debug("Get entity by key {}", key);
        Optional<E> entity = getCrudRepository().findById(key);
        return DaoUtil.getData(entity);
    }

    @Override
    public ListenableFuture<D> findByIdAsync(UUID key) {
        log.debug("Get entity by key async {}", key);
        return service.submit(() -> DaoUtil.getData(getCrudRepository().findById(key)));
    }

    @Override
    public boolean removeById(UUID id) {
        return Boolean.TRUE.equals(inTransaction(() -> {
            getCrudRepository().deleteById(id);
            log.debug("Remove request: {}", id);
            return !getCrudRepository().existsById(id);
        }));
    }

    @Override
    public List<D> find() {
        List<E> entities = Lists.newArrayList(getCrudRepository().findAll());
        return DaoUtil.convertDataList(entities);
    }
}
