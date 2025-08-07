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
package org.thingsboard.mqtt.broker.ssl.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

@Configuration
@ConditionalOnProperty(prefix = "server.ssl", name = "enabled", havingValue = "true")
public class SslWebServerConfig {

    public static final String DEFAULT_BUNDLE_NAME = "default";

    @Bean
    @ConfigurationProperties("server.ssl.credentials")
    public SslCredentialsConfig httpServerSslCredentialsConfig() {
        return new SslCredentialsConfig("HTTP Server SSL Credentials", false);
    }

    @Bean
    public SslBundles sslBundles(SslCredentialsConfig httpServerSslCredentialsConfig) {
        SslStoreBundle storeBundle = SslStoreBundle.of(
                httpServerSslCredentialsConfig.getCredentials().getKeyStore(),
                httpServerSslCredentialsConfig.getCredentials().getKeyPassword(),
                null
        );
        return new SslBundles() {
            @Override
            public SslBundle getBundle(String name) {
                return SslBundle.of(storeBundle);
            }

            @Override
            public List<String> getBundleNames() {
                return List.of(DEFAULT_BUNDLE_NAME);
            }

            @Override
            public void addBundleUpdateHandler(String name, Consumer<SslBundle> handler) {
                // no-op
            }
        };
    }

}
