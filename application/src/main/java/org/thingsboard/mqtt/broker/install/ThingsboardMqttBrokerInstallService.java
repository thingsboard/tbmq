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

    public void performInstall() {
        try {
            if (isUpgrade) {
                log.info("Starting TBMQ Upgrade from version {} ...", upgradeFromVersion);

                switch (upgradeFromVersion) {
                    case "1.0.0":
                    case "1.0.1":
                        log.info("Upgrading TBMQ from version 1.0.1 to 1.1.0 ...");
                        databaseEntitiesUpgradeService.upgradeDatabase("1.0.1");
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
