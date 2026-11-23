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
package org.thingsboard.mqtt.broker.dao.rocksdb.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The {@code tar} tool of the build host, which validates what {@link TarGzWriter} writes. A test that needs it is
 * skipped when it is not on the PATH.
 */
final class SystemTar {

    private static final long TIMEOUT_SEC = 60;

    private SystemTar() {
    }

    static void assumeAvailable() {
        boolean available;
        try {
            available = run("tar", "--version").exitCode() == 0;
        } catch (IOException e) {
            available = false;
        }
        assumeTrue(available, "the tar tool is not on the PATH");
    }

    /**
     * Extracts the gzip-compressed {@code archive} into {@code dir}; fails with tar's output when tar does.
     */
    static void extract(Path archive, Path dir) throws IOException {
        Result result = run("tar", "-xzf", archive.toString(), "-C", dir.toString());
        if (result.exitCode() != 0) {
            throw new IOException("tar -xzf " + archive + " exited with " + result.exitCode() + ": " + result.output());
        }
    }

    /**
     * The verbose listing of the gzip-compressed {@code archive}, whatever tar's exit code.
     */
    static Result list(Path archive) throws IOException {
        return run("tar", "-tvzf", archive.toString());
    }

    static Result run(String... command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(String.join(" ", command) + " did not end in " + TIMEOUT_SEC + " s");
            }
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + String.join(" ", command), e);
        }
    }

    record Result(int exitCode, String output) {
    }

}
