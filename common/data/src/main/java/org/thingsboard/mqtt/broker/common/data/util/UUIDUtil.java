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
package org.thingsboard.mqtt.broker.common.data.util;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.EMPTY_STR;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.HYPHEN;

public class UUIDUtil {

    public static String strUuidReplaceHyphen(String uuid) {
        return uuidReplaceHyphen(uuid);
    }

    public static String randomUuid() {
        return uuidReplaceHyphen(UUID.randomUUID().toString());
    }

    private static String uuidReplaceHyphen(String uuid) {
        return uuid.replace(HYPHEN, EMPTY_STR);
    }

    public static UUID stringToUuid(String uuidStr) {
        if (StringUtils.isEmpty(uuidStr) || uuidStr.length() != 32) { // UUID has 8-4-4-4-12 format
            throw new IllegalArgumentException("Invalid UUID string format");
        }

        // Specifically used StringBuilder. Length of the formatted UUID is 36
        StringBuilder formattedUuid = new StringBuilder(36);
        formattedUuid.append(uuidStr, 0, 8) // First part: 8 characters
                .append(HYPHEN)
                .append(uuidStr, 8, 12) // Second part: 4 characters
                .append(HYPHEN)
                .append(uuidStr, 12, 16) // Third part: 4 characters
                .append(HYPHEN)
                .append(uuidStr, 16, 20) // Fourth part: 4 characters
                .append(HYPHEN)
                .append(uuidStr, 20, 32); // Fifth part: 12 characters

        return UUID.fromString(formattedUuid.toString());
    }

}
