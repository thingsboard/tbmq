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
package org.thingsboard.mqtt.broker.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.pqc.legacy.math.linearalgebra.ByteUtils;

@Slf4j
public class EncryptionUtil {

    private EncryptionUtil() {
    }

    public static String certTrimNewLines(String input) {
        return input.replaceAll("-----BEGIN CERTIFICATE-----", "")
                .replaceAll("\n", "")
                .replaceAll("\r", "")
                .replaceAll("-----END CERTIFICATE-----", "");
    }

    public static String pubkTrimNewLines(String input) {
        return input.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("\n", "")
                .replaceAll("\r", "")
                .replaceAll("-----END PUBLIC KEY-----", "");
    }

    public static String prikTrimNewLines(String input) {
        return input.replaceAll("-----BEGIN EC PRIVATE KEY-----", "")
                .replaceAll("\n", "")
                .replaceAll("\r", "")
                .replaceAll("-----END EC PRIVATE KEY-----", "");
    }


    public static String getSha3Hash(String data) {
        String trimmedData = certTrimNewLines(data);
        byte[] dataBytes = trimmedData.getBytes();
        SHA3Digest md = new SHA3Digest(256);
        md.reset();
        md.update(dataBytes, 0, dataBytes.length);
        byte[] hashedBytes = new byte[256 / 8];
        md.doFinal(hashedBytes, 0);
        return ByteUtils.toHexString(hashedBytes);
    }

    public static String getSha3Hash(String delim, String... tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String token : tokens) {
            if (token != null && !token.isEmpty()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(delim);
                }
                sb.append(token);
            }
        }
        return getSha3Hash(sb.toString());
    }
}
