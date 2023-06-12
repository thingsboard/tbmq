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
package org.thingsboard.mqtt.broker.service.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class InstallScripts {

    public static final String DAO_DIR = "dao";
    public static final String SRC_DIR = "src";
    public static final String MAIN_DIR = "main";
    public static final String RESOURCES_DIR = "resources";

    @Value("${install.data_dir:}")
    private String dataDir;

    public String getDataDir() {
        if (!StringUtils.isEmpty(dataDir)) {
            if (!Paths.get(this.dataDir).toFile().isDirectory()) {
                throw new RuntimeException("'install.data_dir' property value is not a valid directory!");
            }
            return dataDir;
        } else {
            String workDir = System.getProperty("user.dir");
            if (workDir.endsWith(DAO_DIR)) {
                return Paths.get(workDir, SRC_DIR, MAIN_DIR, RESOURCES_DIR).toString();
            } else {
                Path dataDirPath = Paths.get(workDir, DAO_DIR, SRC_DIR, MAIN_DIR, RESOURCES_DIR);
                if (Files.exists(dataDirPath)) {
                    return dataDirPath.toString();
                } else {
                    throw new RuntimeException("Not valid working directory: " + workDir + ". Please use either root project directory, dao module directory or specify valid \"install.data_dir\" property to avoid automatic data directory lookup!");
                }
            }
        }
    }
}
