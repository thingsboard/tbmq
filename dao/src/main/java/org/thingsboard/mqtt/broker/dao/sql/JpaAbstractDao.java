/**
 * Copyright © 2016-2022 The Thingsboard Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.thingsboard.mqtt.broker.dao.sql;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.Dao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.mqtt.broker.dao.model.BaseEntity;
import org.thingsboard.mqtt.broker.dao.util.SqlDao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@SqlDao
public abstract class JpaAbstractDao<E extends BaseEntity<D>, D>
        extends JpaAbstractDaoListeningExecutorService
        implements Dao<D> {

    protected abstract Class<E> getEntityClass();

    protected abstract JpaRepository<E, UUID> getRepository();

    protected void setSearchText(E entity) {
    }

    @Override
    @Transactional
    public D save(D domain) {
        E entity;
        try {
            entity = getEntityClass().getConstructor(domain.getClass()).newInstance(domain);
        } catch (Exception e) {
            log.error("Can't create entity for domain object {}", domain, e);
            throw new IllegalArgumentException("Can't create entity for domain object {" + domain + "}", e);
        }
        setSearchText(entity);
        log.debug("Saving entity {}", entity);
        if (entity.getId() == null) {
            UUID uuid = Uuids.timeBased();
            entity.setId(uuid);
            entity.setCreatedTime(Uuids.unixTimestamp(uuid));
        }
        entity = getRepository().save(entity);
        return DaoUtil.getData(entity);
    }

    @Override
    public D findById(UUID key) {
        log.debug("Get entity by key {}", key);
        Optional<E> entity = getRepository().findById(key);
        return DaoUtil.getData(entity);
    }

    @Override
    public ListenableFuture<D> findByIdAsync(UUID key) {
        log.debug("Get entity by key async {}", key);
        return service.submit(() -> DaoUtil.getData(getRepository().findById(key)));
    }


    @Override
    @Transactional
    public boolean removeById(UUID id) {
        getRepository().deleteById(id);
        log.debug("Remove request: {}", id);
        return !getRepository().existsById(id);
    }

    @Transactional
    public void removeAllByIds(Collection<UUID> ids) {
        JpaRepository<E, UUID> repository = getRepository();
        ids.forEach(repository::deleteById);
    }

    @Override
    public List<D> find() {
        List<E> entities = Lists.newArrayList(getRepository().findAll());
        return DaoUtil.convertDataList(entities);
    }
}
