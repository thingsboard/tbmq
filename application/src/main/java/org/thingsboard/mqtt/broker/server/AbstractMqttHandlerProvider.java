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
package org.thingsboard.mqtt.broker.server;

import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.security.ssl.MqttClientAuthType;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.service.auth.unauthorized.UnauthorizedClientManager;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentials;

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
public abstract class AbstractMqttHandlerProvider {

    @Autowired
    private UnauthorizedClientManager unauthorizedClientManager;

    private SSLContext sslContext;

    public SslHandler getSslHandler(String[] enabledCipherSuites, MqttClientAuthType clientAuthType) {
        if (sslContext == null) {
            sslContext = createSslContext();
        }
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        setClientAuthType(clientAuthType, sslEngine);
        sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
        sslEngine.setEnabledCipherSuites(getEnabledCipherSuites(enabledCipherSuites, sslEngine));
        sslEngine.setEnableSessionCreation(true);
        return new SslHandler(sslEngine);
    }

    private void setClientAuthType(MqttClientAuthType clientAuthType, SSLEngine sslEngine) {
        switch (clientAuthType) {
            case CLIENT_AUTH_REQUIRED -> sslEngine.setNeedClientAuth(true);
            case CLIENT_AUTH_REQUESTED -> sslEngine.setWantClientAuth(true);
        }
    }

    private SSLContext createSslContext() {
        try {
            String sslProtocol = getSslProtocol();

            SslCredentials sslCredentials = getSslCredentials();
            TrustManagerFactory tmFactory = sslCredentials.createTrustManagerFactory();
            KeyManagerFactory kmf = sslCredentials.createKeyManagerFactory();

            KeyManager[] km = kmf.getKeyManagers();
            TrustManager x509wrapped = getX509TrustManager(tmFactory);
            TrustManager[] tm = {x509wrapped};
            if (StringUtils.isEmpty(sslProtocol)) {
                sslProtocol = "TLS";
            } else {
                log.debug("sslProtocol is set to {}", sslProtocol);
            }
            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(km, tm, null);
            return sslContext;
        } catch (Exception e) {
            log.error("Unable to set up SSL context.", e);
            throw new RuntimeException("Failed to get SSL context", e);
        }
    }

    private TrustManager getX509TrustManager(TrustManagerFactory tmf) {
        X509TrustManager x509Tm = null;
        if (tmf.getTrustManagers().length == 0) {
            log.debug("TrustManagers of TrustManagerFactory is empty!");
        }
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509Tm = (X509TrustManager) tm;
                log.debug("Found X509TrustManager {}", x509Tm);
                break;
            }
        }
        if (x509Tm == null) {
            log.warn("X509TrustManager was not found!");
        }
        return new ThingsboardMqttX509TrustManager(x509Tm, unauthorizedClientManager);
    }

    @RequiredArgsConstructor
    static class ThingsboardMqttX509TrustManager implements X509TrustManager {

        private final X509TrustManager trustManager;
        private final UnauthorizedClientManager unauthorizedClientManager;

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager != null ? trustManager.getAcceptedIssuers() : new X509Certificate[0];
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            if (trustManager != null) {
                trustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            // think if better to add credentials validation here
            try {
                if (trustManager != null) {
                    trustManager.checkClientTrusted(chain, authType);
                }
            } catch (CertificateException e) {
                String subject = extractSubjectFromChain(chain);
                log.warn("Rejecting client with leaf cert [{}], chain size - [{}]", subject, chain != null ? chain.length : 0);
                unauthorizedClientManager.persistClientUnauthorized(getUnauthorizedClient(subject, e));
                throw e;
            }
        }

        private String extractSubjectFromChain(X509Certificate[] chain) {
            return (chain != null && chain.length > 0)
                    ? chain[0].getSubjectX500Principal().getName()
                    : null;
        }

        private UnauthorizedClient getUnauthorizedClient(String subject, CertificateException e) {
            return UnauthorizedClient.builder()
                    .clientId(generateUnknownClientId(subject))
                    .ipAddress(BrokerConstants.UNKNOWN) // can be improved later
                    .ts(System.currentTimeMillis())
                    .username(BrokerConstants.UNKNOWN)
                    .passwordProvided(false)
                    .tlsUsed(true)
                    .reason(e.getMessage())
                    .build();
        }

        private String generateUnknownClientId(String subject) {
            return subject != null ? subject : BrokerConstants.UNKNOWN_PREFIX + RandomStringUtils.secure().nextAlphanumeric(6);
        }
    }

    protected abstract String getSslProtocol();

    protected abstract SslCredentials getSslCredentials();

    private String[] getEnabledCipherSuites(String[] enabledCipherSuites, SSLEngine sslEngine) {
        return enabledCipherSuites == null || enabledCipherSuites.length == 0 ? sslEngine.getSupportedCipherSuites() : enabledCipherSuites;
    }
}
