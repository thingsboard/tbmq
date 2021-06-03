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
package org.thingsboard.mqtt.broker.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.install.DatabaseSchemaService;
import org.thingsboard.mqtt.broker.service.install.SystemDataLoaderService;

@Service
@Profile("install")
@Slf4j
public class ThingsboardMqttBrokerInstallService {

    @Value("${install.upgrade:false}")
    private Boolean isUpgrade;

    @Autowired
    private DatabaseSchemaService databaseSchemaService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SystemDataLoaderService systemDataLoaderService;

    public void performInstall() {
        try {
            if (isUpgrade) {
                log.warn("Nothing to upgrade yet...");
            } else {
                log.info("Starting ThingsBoard MQTT Broker Installation...");

                log.info("Installing DataBase schema...");

                databaseSchemaService.createDatabaseSchema();

                log.info("Loading system data...");

                systemDataLoaderService.createAdmin();

                log.info("Installation finished successfully!");
            }
        } catch (Exception e) {
            log.error("Unexpected error during ThingsBoard MQTT Broker installation!", e);
            throw new ThingsboardInstallException("Unexpected error during ThingsBoard installation!", e);
        } finally {
            SpringApplication.exit(context);
        }
    }

}
