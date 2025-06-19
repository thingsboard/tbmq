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
package org.thingsboard.mqtt.broker.common.data.util;

import org.springframework.web.util.UriComponentsBuilder;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class UrlUtils {

    public static URI buildEncodedUri(String endpointUrl) {
        if (endpointUrl == null) {
            throw new RuntimeException("Url string cannot be null!");
        }
        if (endpointUrl.isEmpty()) {
            throw new RuntimeException("Url string cannot be empty!");
        }

        URI uri = UriComponentsBuilder.fromUriString(endpointUrl).build().encode().toUri();
        if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
            throw new RuntimeException("Transport scheme(protocol) must be provided!");
        }

        boolean authorityNotValid = uri.getAuthority() == null || uri.getAuthority().isEmpty();
        boolean hostNotValid = uri.getHost() == null || uri.getHost().isEmpty();
        if (authorityNotValid || hostNotValid) {
            throw new RuntimeException("Url string is invalid!");
        }

        return uri;
    }

    public static URL buildEncodedUrl(String endpointUrl) throws MalformedURLException {
        return buildEncodedUri(endpointUrl).toURL();
    }

    public static void validateUrl(String endpointUrl) {
        try {
            buildEncodedUri(endpointUrl);
        } catch (RuntimeException e) {
            throw new DataValidationException("Invalid url: " + endpointUrl, e);
        }
    }

}
