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
    @Value("${install.upgrade.from_version:1.2.3}")
    private String upgradeFromVersion;

    private final DatabaseSchemaService databaseSchemaService;
    private final ApplicationContext context;
    private final SystemDataLoaderService systemDataLoaderService;
    private final DatabaseEntitiesUpgradeService databaseEntitiesUpgradeService;
    private final CacheCleanupService cacheCleanupService;
    private final DataUpdateService dataUpdateService;

    public void performInstall() {
        try {
            if (isUpgrade) {
                log.info("Starting TBMQ Upgrade from version {} ...", upgradeFromVersion);

                cacheCleanupService.clearCache(upgradeFromVersion);

                switch (upgradeFromVersion) {
                    case "1.0.0":
                    case "1.0.1":
                        log.info("Upgrading TBMQ from version 1.0.1 to 1.1.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.0.1");
                    case "1.1.0":
                        log.info("Upgrading TBMQ from version 1.1.0 to 1.2.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.1.0");
                    case "1.2.0":
                        log.info("Upgrading TBMQ from version 1.2.0 to 1.2.1 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.2.0");
                    case "1.2.1":
                        log.info("Upgrading TBMQ from version 1.2.1 to 1.3.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.2.1");

                        systemDataLoaderService.createWebSocketMqttClientCredentials();
                        systemDataLoaderService.createDefaultWebSocketConnections();
                    case "1.3.0":
                        log.info("Upgrading TBMQ from version 1.3.0 to 1.4.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.3.0");
                        dataUpdateService.updateData("1.3.0");
                    case "1.4.0":
                        log.info("Upgrading TBMQ from version 1.4.0 to 2.0.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.4.0");
                        dataUpdateService.updateData("1.4.0");
                    case "2.0.0":
                        log.info("Upgrading TBMQ from version 2.0.0 to 2.0.1 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("2.0.0");
                        break;
                    default:
                        throw new RuntimeException("Unable to upgrade TBMQ, unsupported fromVersion: " + upgradeFromVersion);
                }

                log.info("Upgrade finished successfully!");
            } else {
                log.info("Starting TBMQ Installation...");

                log.info("Installing DataBase schema...");

                databaseSchemaService.createDatabaseSchema();

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
