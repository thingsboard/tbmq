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
package org.thingsboard.mqtt.broker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.thingsboard.mqtt.broker.install.ThingsboardMqttBrokerInstallService;

import java.util.Arrays;
import java.util.Properties;

@Slf4j
@SpringBootConfiguration
@ComponentScan({"org.thingsboard.mqtt.broker.install",
        "org.thingsboard.mqtt.broker.service.install",
        "org.thingsboard.mqtt.broker.service.user",
        "org.thingsboard.mqtt.broker.dao",
        "org.thingsboard.mqtt.broker.cache",
        "org.thingsboard.mqtt.broker.config.bcrypt"})
public class ThingsboardMqttBrokerInstallApplication {

    private static final String SPRING_CONFIG_NAME_KEY = "--spring.config.name";
    private static final String DEFAULT_SPRING_CONFIG_PARAM = SPRING_CONFIG_NAME_KEY + "=" + "thingsboard-mqtt-broker";

    public static void main(String[] args) {
        try {
            SpringApplication application = new SpringApplication(ThingsboardMqttBrokerInstallApplication.class);
            application.setAdditionalProfiles("install");
            Properties properties = new Properties();
            properties.put("spring.main.web-application-type", "none");
            application.setDefaultProperties(properties);
            ConfigurableApplicationContext context = application.run(updateArguments(args));
            context.getBean(ThingsboardMqttBrokerInstallService.class).performInstall();
        } catch (Exception e) {
            log.error(e.getMessage());
            System.exit(1);
        }
    }

    private static String[] updateArguments(String[] args) {
        if (Arrays.stream(args).noneMatch(arg -> arg.startsWith(SPRING_CONFIG_NAME_KEY))) {
            String[] modifiedArgs = new String[args.length + 1];
            System.arraycopy(args, 0, modifiedArgs, 0, args.length);
            modifiedArgs[args.length] = DEFAULT_SPRING_CONFIG_PARAM;
            return modifiedArgs;
        }
        return args;
    }
}
