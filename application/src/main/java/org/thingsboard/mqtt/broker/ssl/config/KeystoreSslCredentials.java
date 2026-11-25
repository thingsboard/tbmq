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
package org.thingsboard.mqtt.broker.ssl.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.ResourceUtils;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.TrustManagerFactory;

@Data
@EqualsAndHashCode(callSuper = false)
public class KeystoreSslCredentials extends AbstractSslCredentials {

    private String type;
    private String storeFile;
    private String storePassword;
    private String keyPassword;
    private String keyAlias;
    /**
     * Optional, comma-separated: the trusted-certificate entries of the key store that the listener accepts as
     * client-certificate issuers. Unset (the default) keeps the stock behaviour: the TrustManager is built from the
     * whole key store, which includes every certificate of the server key's own chain (added below as root-N), so a
     * public server chain (e.g. Let's Encrypt) also becomes a client CA. When set, only these aliases are trusted for
     * client authentication; each must exist and be a trusted-certificate entry, or the credentials fail to load.
     */
    private String clientTrustAliases;

    private KeyStore clientTrustStore;

    @Override
    protected boolean canUse() {
        return ResourceUtils.resourceExists(this, this.storeFile);
    }

    @Override
    protected KeyStore loadKeyStore(boolean trustsOnly, char[] keyPasswordArray) throws IOException, GeneralSecurityException {
        String keyStoreType = StringUtils.isEmpty(this.type) ? KeyStore.getDefaultType() : this.type;
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream tsFileInputStream = ResourceUtils.getInputStream(this, this.storeFile)) {
            keyStore.load(tsFileInputStream, StringUtils.isEmpty(this.storePassword) ? new char[0] : this.storePassword.toCharArray());
        }
        this.clientTrustStore = buildClientTrustStore(keyStore);
        Enumeration<String> aliases = keyStore.aliases();
        List<Certificate> certificates = new ArrayList<>();
        while (aliases.hasMoreElements()) {
            Certificate[] certificatesArray = keyStore.getCertificateChain(aliases.nextElement());
            if (certificatesArray != null) {
                certificates.addAll(Arrays.asList(certificatesArray));
            }
        }
        int idx = 0;
        for (Certificate certificate : certificates) {
            keyStore.setCertificateEntry("root-" + idx, certificate);
            idx++;
        }
        return keyStore;
    }

    @Override
    public TrustManagerFactory createTrustManagerFactory() throws NoSuchAlgorithmException, KeyStoreException {
        if (this.clientTrustStore == null) {
            return super.createTrustManagerFactory();
        }
        TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmFactory.init(this.clientTrustStore);
        return tmFactory;
    }

    /** The configured client-trust aliases as their own key store, read before the chain is added as root-N. */
    private KeyStore buildClientTrustStore(KeyStore keyStore) throws IOException, GeneralSecurityException {
        if (StringUtils.isBlank(this.clientTrustAliases)) {
            return null;
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        for (String alias : this.clientTrustAliases.split(",")) {
            String name = alias.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!keyStore.containsAlias(name)) {
                throw new IllegalArgumentException("Client trust alias '" + name + "' is not in the key store");
            }
            if (!keyStore.isCertificateEntry(name)) {
                throw new IllegalArgumentException("Client trust alias '" + name + "' is not a trusted-certificate entry");
            }
            trustStore.setCertificateEntry(name, keyStore.getCertificate(name));
        }
        if (trustStore.size() == 0) {
            throw new IllegalArgumentException("Client trust aliases are set but name no entry: '" + this.clientTrustAliases + "'");
        }
        return trustStore;
    }

    @Override
    protected void updateKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }
}
