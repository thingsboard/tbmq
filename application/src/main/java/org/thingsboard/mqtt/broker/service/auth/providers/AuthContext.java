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
package org.thingsboard.mqtt.broker.service.auth.providers;

import io.netty.handler.ssl.SslHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

@Builder
@Getter
@AllArgsConstructor
public class AuthContext {
    private final String clientId;
    private final String username;
    private final byte[] passwordBytes;
    private final SslHandler sslHandler;

    @Override
    public String toString() {
        return "AuthContext{" +
                "clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", password=" + (passwordBytes == null ? "null" : new String(passwordBytes, StandardCharsets.UTF_8)) +
                ", sslHandler=" + sslHandler +
                '}';
    }
}
