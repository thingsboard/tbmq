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
package org.thingsboard.mqtt.broker.ssl.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.ResourceUtils;
import org.thingsboard.mqtt.broker.common.data.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.Iterator;

@Data
@EqualsAndHashCode(callSuper = false)
public class KeystoreSslCredentials extends AbstractSslCredentials {

    private String type;
    private String storeFile;
    private String storePassword;
    private String keyPassword;
    private String keyAlias;

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
        Enumeration<String> aliases = keyStore.aliases();
        int idx = 0;
        for (Iterator<String> it = aliases.asIterator(); it.hasNext(); ) {
            Certificate[] certificates = keyStore.getCertificateChain(it.next());
            if (certificates == null) {
                continue;
            }
            for (Certificate certificate : certificates) {
                keyStore.setCertificateEntry("root-" + idx, certificate);
                idx++;
            }
        }
        return keyStore;
    }

    @Override
    protected void updateKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }
}
