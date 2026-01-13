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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.util;

import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.COLON;

public class BlockedClientKeyUtil {

    public static String generateKey(BlockedClientType type, String value) {
        return generateKey(type, value, null);
    }

    public static String generateKey(BlockedClientType type, String value, RegexMatchTarget target) {
        String key = type.getLabel() + COLON + value;
        if (target == null) {
            return key;
        } else {
            return key + COLON + target.getLabel();
        }
    }

    public static BlockedClientType extractTypeFromKey(String key) {
        if (StringUtils.isEmpty(key) || !key.contains(COLON)) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }
        return BlockedClientType.fromLabel(key.substring(0, key.indexOf(COLON)));
    }

}
