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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test helper that builds a class loader exposing fake {@code META-INF/tbmq-edition} markers.
 */
public final class EditionMarkers {

    private EditionMarkers() {
    }

    /**
     * Creates a fresh directory under {@code dir}, lays out {@code <fresh>/<i>/META-INF/tbmq-edition}
     * for every content and returns a class loader over those directories.
     */
    public static ClassLoader markers(Path dir, ClassLoader parent, String... contents) {
        try {
            Path root = Files.createTempDirectory(dir, "markers");
            URL[] urls = new URL[contents.length];
            for (int i = 0; i < contents.length; i++) {
                Path classpathRoot = root.resolve(String.valueOf(i));
                Path marker = classpathRoot.resolve(TbmqEditionResolver.MARKER_RESOURCE);
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, contents[i], StandardCharsets.UTF_8);
                urls[i] = classpathRoot.toUri().toURL();
            }
            return new URLClassLoader(urls, parent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
