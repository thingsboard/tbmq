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
package org.thingsboard.mqtt.broker.lite;

import org.junit.rules.ExternalResource;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Points {@value #BACKUPS_PATH} of the Lite context at a fresh temporary directory for one test class: sets the system
 * property before the class and deletes the directory after it, restoring the previous value. Used as a
 * {@code @ClassRule}, so the property is set before the Spring context of the class starts.
 */
public class TempBackupsPathRule extends ExternalResource {

    public static final String BACKUPS_PATH = "store.backups.path";

    private Path dir;
    private String previous;

    public Path dir() {
        return dir;
    }

    @Override
    protected void before() throws IOException {
        dir = Files.createTempDirectory("tbmq-lite-backups");
        previous = System.setProperty(BACKUPS_PATH, dir.toString());
    }

    @Override
    protected void after() {
        if (previous == null) {
            System.clearProperty(BACKUPS_PATH);
        } else {
            System.setProperty(BACKUPS_PATH, previous);
        }
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete the backups directory " + dir, e);
        }
    }

}
