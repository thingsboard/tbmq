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
package org.thingsboard.mqtt.broker.common.util;

import com.google.common.net.InetAddresses;

import java.util.regex.Pattern;

public class HostNameValidator {

    private static final String LOCALHOST = "localhost";
    private static final String DOMAIN_NAME_REGEX =
            "^(?=.{1,253}$)(?:(?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}$";
    private static final Pattern DOMAIN_NAME_PATTERN = Pattern.compile(DOMAIN_NAME_REGEX);

    public static boolean isValidHostName(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }
        if (LOCALHOST.equalsIgnoreCase(hostname)) {
            return true;
        }
        if (InetAddresses.isInetAddress(hostname)) {
            return true;
        }
        return DOMAIN_NAME_PATTERN.matcher(hostname).matches();
    }

}
