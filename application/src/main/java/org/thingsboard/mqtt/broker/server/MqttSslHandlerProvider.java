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
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.auth.AuthService;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by valerii.sosliuk on 11/6/16.
 */
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

    @Autowired
    private AuthService authService;

    public SslHandler getSslHandler(Channel ch) {
        try {
            URL tsUrl = Resources.getResource(trustStoreFile);
            File tsFile = new File(tsUrl.toURI());

            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            try (InputStream tsFileInputStream = new FileInputStream(tsFile)) {
                trustStore.load(tsFileInputStream, trustStorePassword.toCharArray());
            }
            tmFactory.init(trustStore);

            URL ksUrl = Resources.getResource(keyStoreFile);
            File ksFile = new File(ksUrl.toURI());
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            try (InputStream ksFileInputStream = new FileInputStream(ksFile)) {
                ks.load(ksFileInputStream, keyStorePassword.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyPassword.toCharArray());

            KeyManager[] km = kmf.getKeyManagers();
            TrustManager x509wrapped = getX509TrustManager(tmFactory);
            TrustManager[] tm = {x509wrapped};
            if (StringUtils.isEmpty(sslProtocol)) {
                sslProtocol = "TLS";
            }

            final SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(kmf);
            sslContextBuilder.sslProvider(SslProvider.JDK).trustManager(tmFactory);
//            SslContext sslContext = sslContextBuilder.build();
//            SSLEngine sslEngine = sslContext.newEngine(ch.alloc());

            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(km, tm, null);
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setWantClientAuth(true);
            sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
            sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
            sslEngine.setEnableSessionCreation(true);
//            return new SslHandler(sslEngine, true);
            return new SslHandler(sslEngine);
        } catch (Exception e) {
            log.error("Unable to set up SSL context. Reason: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get SSL handler", e);
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
        return new ThingsboardMqttX509TrustManager(x509Tm, authService);
    }

    static class ThingsboardMqttX509TrustManager implements X509TrustManager {

        private final X509TrustManager trustManager;
        private AuthService authService;

        ThingsboardMqttX509TrustManager(X509TrustManager trustManager, AuthService authService) {
            this.trustManager = trustManager;
            this.authService = authService;
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
            String credentialsBody = null;
            for (X509Certificate cert : chain) {
                try {
                    //TODO: configure properly
                    credentialsBody = authService.validateCredentials(null).get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
}
