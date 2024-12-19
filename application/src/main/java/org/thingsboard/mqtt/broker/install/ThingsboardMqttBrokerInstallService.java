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
package org.thingsboard.mqtt.broker.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.install.DatabaseEntitiesUpgradeService;
import org.thingsboard.mqtt.broker.service.install.DatabaseSchemaService;
import org.thingsboard.mqtt.broker.service.install.DatabaseSchemaSettingsService;
import org.thingsboard.mqtt.broker.service.install.SystemDataLoaderService;
import org.thingsboard.mqtt.broker.service.install.update.CacheCleanupService;
import org.thingsboard.mqtt.broker.service.install.update.DataUpdateService;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class ThingsboardMqttBrokerInstallService {

    @Value("${install.upgrade:false}")
    private Boolean isUpgrade;

    private final DatabaseSchemaService databaseSchemaService;
    private final ApplicationContext context;
    private final SystemDataLoaderService systemDataLoaderService;
    private final DatabaseEntitiesUpgradeService databaseEntitiesUpgradeService;
    private final CacheCleanupService cacheCleanupService;
    private final DataUpdateService dataUpdateService;
    private final DatabaseSchemaSettingsService databaseSchemaVersionService;

    public void performInstall() {
        try {
            if (isUpgrade) {
                // TODO DON'T FORGET to update SUPPORTED_VERSIONS_FROM in DefaultDatabaseSchemaSettingsService
                databaseSchemaVersionService.validateSchemaSettings();
                String fromVersion = databaseSchemaVersionService.getDbSchemaVersion();
                String toVersion = databaseSchemaVersionService.getPackageSchemaVersion();
                log.info("Starting TBMQ Upgrade from version {} to {} ...", fromVersion, toVersion);
                cacheCleanupService.clearCache();
                // Apply the schema_update.sql script. The script may include DDL statements to change the structure
                // of *existing* tables and DML statements to manipulate the DB records.
                databaseEntitiesUpgradeService.upgradeDatabase();
                // All new tables that do not have any data will be automatically created here.
                databaseSchemaService.createDatabaseSchema();
                // TODO: cleanup update code after each release
                dataUpdateService.updateData();
                databaseSchemaVersionService.updateSchemaVersion();
                log.info("Upgrade finished successfully!");
            } else {
                log.info("Starting TBMQ Installation...");

                log.info("Installing DataBase schema...");

                databaseSchemaService.createDatabaseSchema();
                databaseSchemaVersionService.createSchemaSettings();

                log.info("Loading system data...");

                systemDataLoaderService.createAdmin();
                systemDataLoaderService.createAdminSettings();
                systemDataLoaderService.createWebSocketMqttClientCredentials();
                systemDataLoaderService.createDefaultWebSocketConnection();

                log.info("Installation finished successfully!");
            }
        } catch (Exception e) {
            log.error("Unexpected error during TBMQ installation!", e);
            throw new ThingsboardInstallException("Unexpected error during TBMQ installation!", e);
        } finally {
            SpringApplication.exit(context);
        }
    }

}
