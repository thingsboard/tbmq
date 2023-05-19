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
package org.thingsboard.mqtt.broker.queue.util;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class QueueUtil {

    public static Map<String, String> getConfigs(String properties) {
        if (StringUtils.isEmpty(properties)) {
            return Collections.emptyMap();
        }
        Map<String, String> configs = new HashMap<>();
        for (String property : properties.split(";")) {
            int delimiterPosition = property.indexOf(":");
            String key = property.substring(0, delimiterPosition);
            String value = property.substring(delimiterPosition + 1);
            configs.put(key, value);
        }
        return configs;
    }

    public static void overrideProperties(String name, Properties props, Map<String, String> newProps) {
        newProps.forEach((key, value) -> {
            if (props.containsKey(key)) {
                log.warn("[{}] Property with key {} will be overwritten. Old value - {}, new value - {}",
                        name, key, props.getProperty(key), value);
            }
            props.put(key, value);
        });
    }

}
