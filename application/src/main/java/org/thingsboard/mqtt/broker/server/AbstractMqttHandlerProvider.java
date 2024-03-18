/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.StringUtils;
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

    private SSLContext sslContext;

    public SslHandler getSslHandler(String[] enabledCipherSuites) {
        if (sslContext == null) {
            sslContext = createSslContext();
        }
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(true);
        sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
        sslEngine.setEnabledCipherSuites(getEnabledCipherSuites(enabledCipherSuites, sslEngine));
        sslEngine.setEnableSessionCreation(true);
        return new SslHandler(sslEngine);
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
                if (log.isDebugEnabled()) {
                    log.debug("sslProtocol is set to {}", sslProtocol);
                }
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
            if (log.isDebugEnabled()) {
                log.debug("TrustManagers of TrustManagerFactory is empty!");
            }
        }
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509Tm = (X509TrustManager) tm;
                if (log.isDebugEnabled()) {
                    log.debug("Found X509TrustManager {}", x509Tm);
                }
                break;
            }
        }
        if (x509Tm == null && log.isDebugEnabled()) {
            log.debug("X509TrustManager was not found!");
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

    protected abstract String getSslProtocol();

    protected abstract SslCredentials getSslCredentials();

    private String[] getEnabledCipherSuites(String[] enabledCipherSuites, SSLEngine sslEngine) {
        return enabledCipherSuites == null || enabledCipherSuites.length == 0 ? sslEngine.getSupportedCipherSuites() : enabledCipherSuites;
    }
}
