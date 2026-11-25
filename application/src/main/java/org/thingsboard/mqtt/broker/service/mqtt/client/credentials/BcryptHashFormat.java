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
package org.thingsboard.mqtt.broker.service.mqtt.client.credentials;

import java.util.regex.Pattern;

/**
 * The shape of a BCrypt hash as Spring's {@code BCryptPasswordEncoder} reads it: prefix {@code a$}, {@code b$}
 * or {@code y$}, a two-digit cost from 04 to 14, then 53 characters of salt and digest.
 */
public final class BcryptHashFormat {

    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$(0[4-9]|1[0-4])\\$[./A-Za-z0-9]{53}$");

    private BcryptHashFormat() {
    }

    public static boolean isValid(String hash) {
        return hash != null && BCRYPT_HASH.matcher(hash).matches();
    }

}
