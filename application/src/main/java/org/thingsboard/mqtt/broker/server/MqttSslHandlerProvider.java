/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import com.google.common.io.Resources;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "server.mqtt.ssl", value = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttSslHandlerProvider {

    @Value("${server.mqtt.ssl.protocol}")
    private String sslProtocol;

    @Value("${server.mqtt.ssl.key_store}")
    private String keyStoreFile;
    @Value("${server.mqtt.ssl.key_store_password}")
    private String keyStorePassword;
    @Value("${server.mqtt.ssl.key_password}")
    private String keyPassword;
    @Value("${server.mqtt.ssl.key_store_type}")
    private String keyStoreType;

    @Value("${server.mqtt.ssl.trust_store}")
    private String trustStoreFile;
    @Value("${server.mqtt.ssl.trust_store_password}")
    private String trustStorePassword;
    @Value("${server.mqtt.ssl.trust_store_type}")
    private String trustStoreType;

    public SslHandler getSslHandler() {
        try {
            TrustManagerFactory tmf = initTrustStore();

            KeyManagerFactory kmf = initKeyStore();

            if (StringUtils.isEmpty(sslProtocol)) {
                sslProtocol = "TLS";
            }

            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(true);
            sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
            sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
            sslEngine.setEnableSessionCreation(true);
            return new SslHandler(sslEngine);
        } catch (Exception e) {
            log.error("Unable to set up SSL context. Reason: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get SSL handler", e);
        }
    }

    private KeyManagerFactory initKeyStore() throws Exception {
        URL ksUrl = Resources.getResource(keyStoreFile);
        File ksFile = new File(ksUrl.toURI());
        KeyStore ks = KeyStore.getInstance(keyStoreType);
        try (InputStream ksFileInputStream = new FileInputStream(ksFile)) {
            ks.load(ksFileInputStream, keyStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPassword.toCharArray());
        return kmf;
    }

    private TrustManagerFactory initTrustStore() throws Exception {
        URL tsUrl = Resources.getResource(trustStoreFile);
        File tsFile = new File(tsUrl.toURI());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore trustStore = KeyStore.getInstance(trustStoreType);
        try (InputStream tsFileInputStream = new FileInputStream(tsFile)) {
            trustStore.load(tsFileInputStream, trustStorePassword.toCharArray());
        }
        tmf.init(trustStore);
        return tmf;
    }
}
