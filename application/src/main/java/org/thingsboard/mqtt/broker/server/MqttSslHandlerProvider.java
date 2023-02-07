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
package org.thingsboard.mqtt.broker.server;

import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentials;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentialsConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
@Component("MqttSslHandlerProvider")
@ConditionalOnProperty(prefix = "listener.ssl", value = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttSslHandlerProvider {

    @Value("${listener.ssl.config.protocol}")
    private String sslProtocol;

    @Bean
    @ConfigurationProperties(prefix = "listener.ssl.config.credentials")
    public SslCredentialsConfig mqttSslCredentials() {
        return new SslCredentialsConfig("MQTT SSL Credentials", false);
    }

    @Autowired(required = false)
    @Qualifier("mqttSslCredentials")
    private SslCredentialsConfig mqttSslCredentialsConfig;

    private SSLContext sslContext;

    public SslHandler getSslHandler() {
        if (sslContext == null) {
            sslContext = createSslContext();
        }
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(true);
        sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
        sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
        sslEngine.setEnableSessionCreation(true);
        return new SslHandler(sslEngine);
    }

    private SSLContext createSslContext() {
        try {
            SslCredentials sslCredentials = this.mqttSslCredentialsConfig.getCredentials();
            TrustManagerFactory tmFactory = sslCredentials.createTrustManagerFactory();
            KeyManagerFactory kmf = sslCredentials.createKeyManagerFactory();

            KeyManager[] km = kmf.getKeyManagers();
            TrustManager x509wrapped = getX509TrustManager(tmFactory);
            TrustManager[] tm = {x509wrapped};
            if (StringUtils.isEmpty(sslProtocol)) {
                sslProtocol = "TLS";
            }
            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(km, tm, null);
            return sslContext;
        } catch (Exception e) {
            log.error("Unable to set up SSL context. Reason: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get SSL context", e);
        }
    }

    private TrustManager getX509TrustManager(TrustManagerFactory tmf) throws Exception {
        X509TrustManager x509Tm = null;
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509Tm = (X509TrustManager) tm;
                break;
            }
        }
        return new ThingsboardMqttX509TrustManager(x509Tm);
    }

    static class ThingsboardMqttX509TrustManager implements X509TrustManager {

        private final X509TrustManager trustManager;

        ThingsboardMqttX509TrustManager(X509TrustManager trustManager) {
            this.trustManager = trustManager;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            trustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            // think if better to add credentials validation here
        }
    }
}
