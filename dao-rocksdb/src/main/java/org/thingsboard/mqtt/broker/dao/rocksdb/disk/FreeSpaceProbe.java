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
package org.thingsboard.mqtt.broker.dao.rocksdb.disk;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads the space of the volume that holds a path. Public so a test can replace the default bean and drive
 * {@link DiskSpaceMonitor#check()}.
 */
@FunctionalInterface
public interface FreeSpaceProbe {

    /**
     * @param path the store path; it may not exist yet
     */
    Space probe(Path path) throws IOException;

    /**
     * @param usableBytes bytes this process can still write to the volume
     * @param totalBytes  size of the volume
     */
    record Space(long usableBytes, long totalBytes) {
    }

}
