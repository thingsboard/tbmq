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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the push script with {@code DRY_RUN=1}, so that the tags it chooses are checked without a registry or Docker.
 */
public class PushScriptTest {

    private static final String REPO = "thingsboard/tbmq-lite";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path script;

    @Before
    public void setUp() {
        String path = System.getProperty("tbmq.lite.push-script");
        assertNotNull("system property tbmq.lite.push-script (the push script's path) is not set", path);
        script = new File(path).toPath().toAbsolutePath().normalize();
        assertTrue("push script not found: " + script, Files.isRegularFile(script));
    }

    @Test
    public void betaPushesOnlyItsVersionTag() throws Exception {
        assertVersionTagOnly("2.5.0-beta");
    }

    @Test
    public void snapshotPushesOnlyItsVersionTag() throws Exception {
        assertVersionTagOnly("2.5.0-SNAPSHOT");
    }

    @Test
    public void releaseCandidatePushesOnlyItsVersionTag() throws Exception {
        assertVersionTagOnly("2.5.0-rc1");
    }

    @Test
    public void releasePushesOnlyItsVersionTag() throws Exception {
        assertVersionTagOnly("2.5.0");
    }

    @Test
    public void noVersionEverTagsLatest() throws Exception {
        for (String version : new String[]{"2.5.0", "10.21.134", "2.5", "2.5.0.1", "v2.5.0", "2.5.0PE"}) {
            assertVersionTagOnly(version);
        }
    }

    @Test
    public void dryRunDoesNotInvokeDocker() throws Exception {
        Path bin = tmp.newFolder("bin").toPath();
        Path marker = tmp.getRoot().toPath().resolve("docker-called");
        Path fakeDocker = bin.resolve("docker");
        Files.write(fakeDocker, ("#!/bin/sh\ntouch '" + marker + "'\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(fakeDocker.toFile().setExecutable(true));

        Result result = runWithPath(true, bin.toString(), REPO, "2.5.0");

        assertEquals(result.stderr, 0, result.exit);
        assertFalse("docker was invoked in dry-run mode", Files.exists(marker));
    }

    @Test
    public void withoutDryRunItInvokesDockerBuildx() throws Exception {
        Path bin = tmp.newFolder("bin").toPath();
        Path args = tmp.getRoot().toPath().resolve("docker-args");
        Path fakeDocker = bin.resolve("docker");
        Files.write(fakeDocker, ("#!/bin/sh\nprintf '%s\\n' \"$@\" > '" + args + "'\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(fakeDocker.toFile().setExecutable(true));

        assertFakeDockerWins(bin, fakeDocker);

        Result result = runWithPath(false, bin.toString(), REPO, "2.5.0-beta");

        assertEquals(result.stderr, 0, result.exit);
        List<String> lines = Files.readAllLines(args, StandardCharsets.UTF_8);
        assertEquals("buildx", lines.get(0));
        assertEquals("build", lines.get(1));
        assertTrue(lines.toString(), lines.contains("--platform=linux/amd64,linux/arm64"));
        assertTrue(lines.toString(), lines.contains("--push"));
        assertTrue(lines.toString(), lines.contains(REPO + ":2.5.0-beta"));
        assertFalse(lines.toString(), lines.toString().contains(":latest"));
        assertEquals(".", lines.get(lines.size() - 1));
    }

    @Test
    public void unknownDryRunValueIsRefusedWithoutCallingDocker() throws Exception {
        for (String value : new String[]{"true", "0", "yes", "11"}) {
            Path bin = tmp.newFolder().toPath();
            Path marker = tmp.getRoot().toPath().resolve("docker-called-" + bin.getFileName());
            Path fakeDocker = bin.resolve("docker");
            Files.write(fakeDocker, ("#!/bin/sh\ntouch '" + marker + "'\n").getBytes(StandardCharsets.UTF_8));
            assertTrue(fakeDocker.toFile().setExecutable(true));

            Result result = runWithDryRun(value, bin.toString(), REPO, "2.5.0");

            assertEquals(value + ": " + result.stdout, 1, result.exit);
            assertTrue(value + ": " + result.stderr, result.stderr.contains("DRY_RUN"));
            assertTrue(value + ": " + result.stderr, result.stderr.contains("unset, empty or 1"));
            assertTrue(value + ": nothing may be printed as a command: " + result.stdout, result.stdout.isEmpty());
            assertFalse(value + ": docker was invoked", Files.exists(marker));
        }
    }

    @Test
    public void emptyDryRunIsARealRun() throws Exception {
        Path bin = tmp.newFolder("bin").toPath();
        Path marker = tmp.getRoot().toPath().resolve("docker-called");
        Path fakeDocker = bin.resolve("docker");
        Files.write(fakeDocker, ("#!/bin/sh\ntouch '" + marker + "'\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(fakeDocker.toFile().setExecutable(true));

        assertFakeDockerWins(bin, fakeDocker);

        Result result = runWithDryRun("", bin.toString(), REPO, "2.5.0-beta");

        assertEquals(result.stderr, 0, result.exit);
        assertTrue("docker was not invoked", Files.exists(marker));
    }

    @Test
    public void whitespaceInRepositoryOrVersionFailsWithUsage() throws Exception {
        for (String bad : new String[]{"2.5.0 beta", "2.5.0\n", "2.5.0\t", " 2.5.0"}) {
            assertUsageFailure(run(true, REPO, bad));
            assertUsageFailure(run(true, bad, "2.5.0"));
        }
    }

    @Test
    public void noArgumentsFailsWithUsage() throws Exception {
        assertUsageFailure(run(true));
    }

    @Test
    public void missingVersionFailsWithUsage() throws Exception {
        assertUsageFailure(run(true, REPO));
    }

    @Test
    public void emptyArgumentsFailWithUsage() throws Exception {
        assertUsageFailure(run(true, REPO, ""));
        assertUsageFailure(run(true, "", "2.5.0"));
    }

    @Test
    public void extraArgumentsFailWithUsage() throws Exception {
        assertUsageFailure(run(true, REPO, "2.5.0", "extra"));
    }

    private void assertVersionTagOnly(String version) throws Exception {
        Result result = run(true, REPO, version);

        assertEquals(result.stderr, 0, result.exit);
        assertEquals(result.stdout.toString(), 1, result.stdout.size());
        String command = result.stdout.get(0);
        assertBuildCommand(command);
        assertEquals(command, 1, count(command, " -t "));
        assertTrue(command, command.contains(" -t " + REPO + ":" + version + " "));
        assertFalse(command, command.contains("latest"));
    }

    /**
     * A real-mode run must never reach the real docker: bash skips a fake it cannot execute (a noexec temp dir), so
     * check first that the test PATH resolves "docker" to the fake.
     */
    private static void assertFakeDockerWins(Path bin, Path fakeDocker) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", "command -v docker");
        pb.environment().put("PATH", bin + File.pathSeparator + pb.environment().get("PATH"));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String resolved = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue("docker lookup timed out", process.waitFor(30, TimeUnit.SECONDS));
        assertEquals("the fake docker is not what the test PATH resolves docker to (is the temp dir noexec?)",
                fakeDocker.toString(), resolved);
    }

    private static void assertBuildCommand(String command) {
        assertTrue(command, command.startsWith("docker buildx build "));
        assertTrue(command, command.contains(" --platform=linux/amd64,linux/arm64 "));
        assertTrue(command, command.contains(" --push "));
        assertTrue(command, command.endsWith(" ."));
    }

    private static void assertUsageFailure(Result result) {
        assertEquals(result.stdout + result.stderr, 1, result.exit);
        assertTrue(result.stderr, result.stderr.contains("Usage:"));
        assertTrue("nothing may be printed as a command: " + result.stdout, result.stdout.isEmpty());
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private Result run(boolean dryRun, String... args) throws Exception {
        return runWithPath(dryRun, null, args);
    }

    private Result runWithPath(boolean dryRun, String extraPath, String... args) throws Exception {
        return runWithDryRun(dryRun ? "1" : null, extraPath, args);
    }

    private Result runWithDryRun(String dryRun, String extraPath, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.toString());
        for (String arg : args) {
            command.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().remove("DRY_RUN");
        if (dryRun != null) {
            pb.environment().put("DRY_RUN", dryRun);
        }
        if (extraPath != null) {
            pb.environment().put("PATH", extraPath + File.pathSeparator + pb.environment().get("PATH"));
        }
        Path stdoutFile = tmp.newFile().toPath();
        Path stderrFile = tmp.newFile().toPath();
        pb.redirectOutput(stdoutFile.toFile());
        pb.redirectError(stderrFile.toFile());
        Process process = pb.start();
        assertTrue("push script timed out", process.waitFor(30, TimeUnit.SECONDS));
        Result result = new Result();
        result.exit = process.exitValue();
        result.stdout = Files.readAllLines(stdoutFile, StandardCharsets.UTF_8);
        result.stderr = new String(Files.readAllBytes(stderrFile), StandardCharsets.UTF_8);
        return result;
    }

    private static class Result {
        int exit;
        List<String> stdout;
        String stderr;
    }
}
