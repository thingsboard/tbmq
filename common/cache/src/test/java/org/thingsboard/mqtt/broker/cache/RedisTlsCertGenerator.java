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
package org.thingsboard.mqtt.broker.cache;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test utility for generating self-signed PKI artifacts (CA, server, client certificates)
 * used by Redis TLS tests.
 */
class RedisTlsCertGenerator {

    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    static X509Certificate generateCaCert(KeyPair caKeyPair) throws Exception {
        long now = System.currentTimeMillis();
        X500Principal subject = new X500Principal("CN=Redis-Test-CA,O=TBMQ-Test");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(SERIAL.getAndIncrement()),
                new Date(now), new Date(now + TimeUnit.DAYS.toMillis(365)),
                subject, caKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    /**
     * Generates a certificate signed by the provided CA.
     *
     * @param addLocalhostSan when {@code true}, adds SAN entries for {@code localhost} and {@code 127.0.0.1};
     *                        required for server certificates used in TLS connections to localhost.
     */
    static X509Certificate generateSignedCert(
            KeyPair keyPair, KeyPair caKeyPair, X509Certificate caCert, String cn, boolean addLocalhostSan) throws Exception {
        long now = System.currentTimeMillis();
        X500Principal subject = new X500Principal("CN=" + cn + ",O=TBMQ-Test");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Principal(caCert.getSubjectX500Principal().getName()),
                BigInteger.valueOf(SERIAL.getAndIncrement()),
                new Date(now), new Date(now + TimeUnit.DAYS.toMillis(365)),
                subject, keyPair.getPublic());
        if (addLocalhostSan) {
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[]{
                    new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    new GeneralName(GeneralName.dNSName, "localhost")
            }));
        }
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    static X509Certificate generateSignedCert(
            KeyPair keyPair, KeyPair caKeyPair, X509Certificate caCert, String cn) throws Exception {
        return generateSignedCert(keyPair, caKeyPair, caCert, cn, false);
    }

    static void writePem(Path file, Object obj) throws Exception {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
            writer.flush();
            Files.writeString(file, sw.toString());
        }
    }
}
