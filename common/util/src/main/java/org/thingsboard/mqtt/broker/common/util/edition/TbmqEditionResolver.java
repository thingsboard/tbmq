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
package org.thingsboard.mqtt.broker.common.util.edition;

import org.springframework.util.ConcurrentReferenceHashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link TbmqEdition} from the {@value #MARKER_RESOURCE} classpath resource.
 * Nothing else (YAML, environment, system properties) influences the edition.
 */
public final class TbmqEditionResolver {

    public static final String MARKER_RESOURCE = "META-INF/tbmq-edition";

    private static final Map<ClassLoader, TbmqEdition> CACHE =
            new ConcurrentReferenceHashMap<>(16);

    private TbmqEditionResolver() {
    }

    /**
     * Only successful resolutions are cached, so a failure is reported again on the next call.
     */
    public static TbmqEdition resolve(ClassLoader classLoader) {
        TbmqEdition cached = CACHE.get(classLoader);
        if (cached != null) {
            return cached;
        }
        TbmqEdition edition = doResolve(classLoader);
        CACHE.put(classLoader, edition);
        return edition;
    }

    private static TbmqEdition doResolve(ClassLoader classLoader) {
        List<URL> markers;
        try {
            markers = Collections.list(classLoader.getResources(MARKER_RESOURCE));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to look up " + MARKER_RESOURCE + " on the classpath", e);
        }
        if (markers.isEmpty()) {
            throw new IllegalStateException("No " + MARKER_RESOURCE + " marker found on the classpath. "
                    + "The image lacks its infrastructure modules, it must contain exactly one edition module.");
        }
        Map<TbmqEdition, List<URL>> urlsByEdition = new LinkedHashMap<>();
        for (URL marker : markers) {
            TbmqEdition edition = TbmqEdition.fromMarkerValue(readValue(marker));
            urlsByEdition.computeIfAbsent(edition, e -> new ArrayList<>()).add(marker);
        }
        if (urlsByEdition.size() > 1) {
            StringBuilder message = new StringBuilder("Conflicting " + MARKER_RESOURCE + " markers on the classpath:");
            urlsByEdition.forEach((edition, urls) ->
                    message.append(" '").append(edition.getMarkerValue()).append("' in ").append(urls).append(';'));
            throw new IllegalStateException(message.toString());
        }
        return urlsByEdition.keySet().iterator().next();
    }

    private static String readValue(URL marker) {
        try {
            URLConnection connection = marker.openConnection();
            connection.setUseCaches(false);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String value = line.trim();
                    if (!value.isEmpty() && !value.startsWith("#")) {
                        return value;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + marker, e);
        }
        throw new IllegalStateException("The " + MARKER_RESOURCE + " marker " + marker + " has no value line");
    }

}
