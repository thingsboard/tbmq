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
package org.thingsboard.mqtt.broker.ssl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentials;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentialsConfig;

import static org.thingsboard.mqtt.broker.ssl.config.SslWebServerConfig.DEFAULT_BUNDLE_NAME;

@Component
@ConditionalOnExpression("'${spring.main.web-environment:true}'=='true' && '${server.ssl.enabled:false}'=='true'")
@RequiredArgsConstructor
public class SslCredentialsWebServerCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final SslCredentialsConfig httpServerSslCredentialsConfig;
    private final SslBundles sslBundles;
    private final ServerProperties serverProperties;

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        SslCredentials sslCredentials = httpServerSslCredentialsConfig.getCredentials();
        Ssl ssl = serverProperties.getSsl();
        ssl.setKeyAlias(sslCredentials.getKeyAlias());
        ssl.setKeyPassword(sslCredentials.getKeyPassword());
        ssl.setBundle(DEFAULT_BUNDLE_NAME);
        ssl.setClientAuth(ClientAuth.NONE);
        factory.setSsl(ssl);
        factory.setSslBundles(sslBundles);
    }

}
