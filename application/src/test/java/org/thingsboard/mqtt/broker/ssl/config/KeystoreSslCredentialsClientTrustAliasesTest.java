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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A PKCS12 key store whose key entry carries a server chain from a public CA (leaf, intermediate, root) plus the
 * client CAs the operator imports as trusted entries. Without client_trust_aliases the listener's TrustManager is
 * built from the whole key store after every chain certificate is added as root-N, so the public chain's CAs are
 * accepted as client-certificate issuers too. client_trust_aliases narrows it to the named trusted entries.
 */
public class KeystoreSslCredentialsClientTrustAliasesTest {

    private static final char[] PW = "store-pw".toCharArray();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private KeyPair publicCaKey;
    private X509Certificate publicCa;      // stands in for a public CA intermediate in the server chain
    private X509Certificate deviceCa;  // the device CA
    private KeyPair deviceKey;
    private X509Certificate serviceCa;     // a second client CA
    private File storeFile;

    @Before
    public void buildKeyStore() throws Exception {
        publicCaKey = keyPair();
        publicCa = cert("CN=Public Intermediate", publicCaKey, "CN=Public Intermediate", publicCaKey.getPrivate(), true);
        deviceKey = keyPair();
        deviceCa = cert("CN=device-ca", deviceKey, "CN=device-ca", deviceKey.getPrivate(), true);
        KeyPair serviceKey = keyPair();
        serviceCa = cert("CN=service-ca", serviceKey, "CN=service-ca", serviceKey.getPrivate(), true);
        KeyPair serverKey = keyPair();
        X509Certificate serverLeaf = cert("CN=devbroker.example", serverKey, "CN=Public Intermediate", publicCaKey.getPrivate(), false);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("certificate", serverKey.getPrivate(), PW, new java.security.cert.Certificate[]{serverLeaf, publicCa});
        ks.setCertificateEntry("device-ca", deviceCa);
        ks.setCertificateEntry("service-ca", serviceCa);
        storeFile = tmp.newFile("server.p12");
        try (FileOutputStream out = new FileOutputStream(storeFile)) {
            ks.store(out, PW);
        }
    }

    @Test
    public void withoutAliasesTheServerChainIsAlsoAClientCa() throws Exception {
        // Stock behaviour, unchanged by default: the public intermediate is an accepted issuer.
        X509TrustManager tm = trustManager(credentials(null));
        assertThat(subjects(tm)).contains("CN=Public Intermediate", "CN=device-ca", "CN=service-ca");
        assertThatCode(() -> tm.checkClientTrusted(new X509Certificate[]{clientIssuedBy(publicCaKey.getPrivate(), "CN=Public Intermediate")}, "ECDHE_ECDSA"))
                .doesNotThrowAnyException();
    }

    @Test
    public void clientTrustAliasesLimitTheAcceptedClientIssuers() throws Exception {
        X509TrustManager tm = trustManager(credentials("device-ca"));
        assertThat(subjects(tm)).containsExactly("CN=device-ca");
        assertThatCode(() -> tm.checkClientTrusted(new X509Certificate[]{clientIssuedBy(deviceKey.getPrivate(), "CN=device-ca")}, "ECDHE_ECDSA"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tm.checkClientTrusted(new X509Certificate[]{clientIssuedBy(publicCaKey.getPrivate(), "CN=Public Intermediate")}, "ECDHE_ECDSA"))
                .isInstanceOf(CertificateException.class);
    }

    @Test
    public void severalAliasesWithSpacesAreAllTrusted() throws Exception {
        X509TrustManager tm = trustManager(credentials(" device-ca , service-ca ,"));
        assertThat(subjects(tm)).containsExactlyInAnyOrder("CN=device-ca", "CN=service-ca");
    }

    @Test
    public void anUnknownAliasFailsTheCredentials() {
        assertThatThrownBy(() -> credentials("device-ca,no-such-ca")).hasMessageContaining("no-such-ca");
    }

    @Test
    public void theServerKeyEntryIsNotAcceptedAsAClientCa() {
        assertThatThrownBy(() -> credentials("certificate")).hasMessageContaining("not a trusted-certificate entry");
    }

    @Test
    public void aliasesThatNameNothingFailTheCredentials() {
        assertThatThrownBy(() -> credentials(" , ")).hasMessageContaining("name no entry");
    }

    private KeystoreSslCredentials credentials(String aliases) throws Exception {
        KeystoreSslCredentials c = new KeystoreSslCredentials();
        c.setType("PKCS12");
        c.setStoreFile(storeFile.getAbsolutePath());
        c.setStorePassword(new String(PW));
        c.setKeyPassword(new String(PW));
        c.setClientTrustAliases(aliases);
        c.init(false);
        return c;
    }

    private static X509TrustManager trustManager(KeystoreSslCredentials c) throws Exception {
        TrustManager[] tms = c.createTrustManagerFactory().getTrustManagers();
        return (X509TrustManager) Arrays.stream(tms).filter(t -> t instanceof X509TrustManager).findFirst().orElseThrow();
    }

    private static Set<String> subjects(X509TrustManager tm) {
        return Arrays.stream(tm.getAcceptedIssuers()).map(x -> x.getSubjectX500Principal().getName()).collect(Collectors.toSet());
    }

    private X509Certificate clientIssuedBy(PrivateKey issuerKey, String issuerDn) throws Exception {
        KeyPair client = keyPair();
        return cert("CN=019953f0-76a0-7f0c-ab8d-705ad7c12aab", client, issuerDn, issuerKey, false);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(256);
        return g.generateKeyPair();
    }

    private static X509Certificate cert(String subject, KeyPair subjectKey, String issuer, PrivateKey issuerKey, boolean ca) throws Exception {
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(new X500Name(issuer), BigInteger.valueOf(now + subject.hashCode()),
                new Date(now - 60_000), new Date(now + 86_400_000L), new X500Name(subject), subjectKey.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        return new JcaX509CertificateConverter().getCertificate(b.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)));
    }
}
