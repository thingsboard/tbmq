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
package org.thingsboard.mqtt.broker.dao.sql.settings;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.sql.AdminSettingsEntity;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;

import java.util.UUID;

@Component
@Slf4j
public class DefaultAdminSettingsDao extends AbstractDao<AdminSettingsEntity, AdminSettings> implements AdminSettingsDao {

    @Autowired
    private AdminSettingsRepository adminSettingsRepository;

    @Override
    protected Class<AdminSettingsEntity> getEntityClass() {
        return AdminSettingsEntity.class;
    }

    @Override
    protected CrudRepository<AdminSettingsEntity, UUID> getCrudRepository() {
        return adminSettingsRepository;
    }

    @Override
    public AdminSettings findByKey(String key) {
        return DaoUtil.getData(adminSettingsRepository.findByKey(key));
    }

    @Override
    @Transactional
    public void removeByKey(String key) {
        if (adminSettingsRepository.existsByKey(key)) {
            adminSettingsRepository.deleteByKey(key);
        }
    }
}
